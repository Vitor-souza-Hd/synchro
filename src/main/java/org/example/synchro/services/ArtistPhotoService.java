package org.example.synchro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.config.LastFmConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
public class ArtistPhotoService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern MUSICAL = Pattern.compile(
            "\\b(singer|songwriter|musician|rapper|vocalist|record producer|musical group|music group|musical duo|" +
            "rock band|pop band|metal band|punk band|indie band|hip hop group|hip-hop group|folk rock band|disc jockey|" +
            "cantor|cantora|compositor|compositora|musico|banda|grupo musical|dupla musical|produtor musical|produtora musical)\\b");
    private final ArtistPhotoStore store;
    private final Duration timeout;
    private final WebClient lastFm;
    private final LastFmConfig lastFmConfig;
    private WebClient lastFmSite = WebClient.builder().baseUrl("https://www.last.fm")
            .defaultHeader("User-Agent", "SynchroTCC/0.1 (https://github.com/Vitor-souza-Hd/synchro)")
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)).build();
    private WebClient wikidata = WebClient.builder().baseUrl("https://www.wikidata.org/w/api.php")
            .defaultHeader("User-Agent", "SynchroTCC/0.1 (https://github.com/Vitor-souza-Hd/synchro)")
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    private final ConcurrentHashMap<String, Mono<Photo>> cache = new ConcurrentHashMap<>();
    public record Photo(String status, String imgUrl) {}

    public ArtistPhotoService(ArtistPhotoStore store, WebClient lastFm, LastFmConfig lastFmConfig,
            @Value("${synchro.artist-photo.timeout:8s}") Duration timeout) {
        this.store = store;
        this.lastFm = lastFm;
        this.lastFmConfig = lastFmConfig;
        this.timeout = timeout;
    }

    public Mono<Photo> find(long id, boolean replaceBroken) {
        return Mono.fromCallable(() -> store.reference(id)).subscribeOn(Schedulers.boundedElastic()).flatMap(reference -> {
            if (!replaceBroken && ArtistPhotoStore.hasUsableImage(reference.image())) return Mono.just(new Photo("found", reference.image()));
            String key = id + ":" + reference.name() + ":" + replaceBroken;
            if (cache.size() >= 500 && !cache.containsKey(key)) cache.clear();
            return cache.computeIfAbsent(key, ignored -> Mono.defer(() -> {
                var unavailable = new AtomicBoolean();
                return lastFmPhoto(reference.name())
                    .filter(url -> !replaceBroken || !url.equals(reference.image()))
                    .timeout(timeout.dividedBy(3)).onErrorResume(error -> { unavailable.set(true); return Mono.empty(); })
                    .switchIfEmpty(Mono.defer(() -> lastFmPublicPhoto(reference.name())
                            .filter(url -> !replaceBroken || !url.equals(reference.image()))
                            .timeout(timeout.dividedBy(3))
                            .onErrorResume(error -> { unavailable.set(true); return Mono.empty(); })))
                    .switchIfEmpty(Mono.defer(() -> lookup(reference.name())))
                    .timeout(timeout)
                    .map(url -> new Photo("found", url))
                    .switchIfEmpty(Mono.fromSupplier(() -> new Photo(unavailable.get() ? "unavailable" : "not_found", null)))
                    .onErrorReturn(new Photo("unavailable", null));
                })
                    .flatMap(photo -> photo.imgUrl() == null ? Mono.just(photo)
                            : Mono.fromCallable(() -> new Photo("found", store.savePhoto(reference, photo.imgUrl(), replaceBroken)))
                                    .subscribeOn(Schedulers.boundedElastic()))
                    .cache(photo -> Duration.ofSeconds(switch (photo.status()) {
                        case "found" -> 86400; case "not_found" -> 1800; default -> 15;
                    }), error -> Duration.ZERO, () -> Duration.ZERO));
        });
    }

    private Mono<String> lastFmPublicPhoto(String name) {
        String path = "/music/" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20") + "/+images";
        return lastFmSite.get().uri(java.net.URI.create("https://www.last.fm" + path))
                .retrieve().bodyToMono(String.class)
                .flatMap(html -> Mono.justOrEmpty(LastFmArtistPage.photo(html, name)));
    }

    private Mono<String> lastFmPhoto(String name) {
        if (lastFmConfig.getApiKey() == null || lastFmConfig.getApiKey().isBlank()) return Mono.empty();
        return lastFm.get().uri(builder -> builder.queryParam("method", "artist.getInfo")
                        .queryParam("artist", "{name}").queryParam("autocorrect", 1)
                        .queryParam("api_key", lastFmConfig.getApiKey()).queryParam("format", "json").build(name))
                .retrieve().bodyToMono(String.class).map(this::json).flatMap(data -> {
                    var artist = data.path("artist");
                    if (data.has("error") || !matches(name, artist.path("name").asText())) return Mono.empty();
                    return Mono.justOrEmpty(LastFmCatalogStore.nodes(artist.path("image")).stream()
                            .sorted(Comparator.comparingInt((JsonNode image) -> switch (image.path("size").asText()) {
                                case "mega" -> 5; case "extralarge" -> 4; case "large" -> 3;
                                case "medium" -> 2; case "small" -> 1; default -> 0;
                            }).reversed())
                            .map(image -> image.path("#text").asText())
                            .filter(ArtistPhotoStore::hasUsableImage).filter(url -> url.startsWith("https://"))
                            .findFirst());
                });
    }

    private Mono<String> lookup(String name) {
        return Flux.fromIterable(List.of("en", "pt")).concatMap(language -> wikidata.get()
                .uri(builder -> builder.queryParam("action", "wbsearchentities").queryParam("search", "{name}")
                        .queryParam("language", language).queryParam("type", "item").queryParam("limit", 10)
                        .queryParam("format", "json").build(name))
                .retrieve().bodyToMono(String.class).map(this::json)
                .flatMapMany(data -> Flux.fromIterable(LastFmCatalogStore.nodes(data.path("search"))))
                .filter(candidate -> matches(name, candidate.path("label").asText())
                        && musical(candidate.path("description").asText()) && candidate.path("id").asText().matches("Q[1-9][0-9]*"))
                .take(4).concatMap(candidate -> entityPhoto(candidate.path("id").asText(), name))).next();
    }

    private Mono<String> entityPhoto(String id, String name) {
        return wikidata.get().uri(builder -> builder.queryParam("action", "wbgetentities").queryParam("ids", id)
                        .queryParam("props", "labels|descriptions|claims").queryParam("languages", "en|pt")
                        .queryParam("format", "json").build())
                .retrieve().bodyToMono(String.class).map(this::json)
                .flatMap(data -> {
                    var entity = data.path("entities").path(id);
                    boolean labelMatches = List.of("en", "pt").stream()
                            .anyMatch(lang -> matches(name, entity.path("labels").path(lang).path("value").asText()));
                    boolean musician = List.of("en", "pt").stream()
                            .anyMatch(lang -> musical(entity.path("descriptions").path(lang).path("value").asText()));
                    if (!labelMatches || !musician) return Mono.empty();
                    return Flux.fromIterable(LastFmCatalogStore.nodes(entity.path("claims").path("P18")))
                            .filter(claim -> !"deprecated".equals(claim.path("rank").asText()))
                            .map(claim -> claim.path("mainsnak").path("datavalue").path("value").asText())
                            .filter(filename -> !filename.isBlank() && filename.length() <= 300)
                            .map(filename -> "https://commons.wikimedia.org/wiki/Special:FilePath/" +
                                    URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20") + "?width=500")
                            .next();
                });
    }

    private JsonNode json(String body) {
        try { return JSON.readTree(body); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Resposta inválida do serviço de imagens", error); }
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('_', ' ').replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
    private static boolean matches(String expected, String actual) {
        String name = normalize(expected), label = normalize(actual);
        return label.equals(name) || label.startsWith(name + " (");
    }
    private static boolean musical(String description) { return MUSICAL.matcher(normalize(description)).find(); }
}
