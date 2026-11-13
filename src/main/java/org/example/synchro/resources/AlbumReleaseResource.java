package org.example.synchro.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.synchro.entities.CatalogName;
import org.example.synchro.services.DailyPopularSelections;
import org.example.synchro.services.DailyPopularSelections.PopularAlbum;
import org.example.synchro.services.LastFmCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.http.HttpClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/catalog/albums")
public class AlbumReleaseResource {
    private static final Set<String> MARKETS = Set.of("br", "us");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient charts = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final LastFmDiscoveryResource lastFm;
    private final LastFmCatalogService catalog;
    private final DailyPopularSelections dailySelections;
    private final Mono<List<PopularAlbum>> dailyAlbums;

    public AlbumReleaseResource(LastFmDiscoveryResource lastFm, LastFmCatalogService catalog,
                                DailyPopularSelections dailySelections) {
        this.lastFm = lastFm;
        this.catalog = catalog;
        this.dailySelections = dailySelections;
        dailyAlbums = Mono.defer(() -> {
                    var today = LocalDate.now(DailyPopularSelections.TIME_ZONE);
                    return Mono.fromCallable(() -> dailySelections.findAlbums(today))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(saved -> saved.map(Mono::just).orElseGet(() -> loadDailyAlbums(today)));
                })
                .cache(albums -> {
                    if (albums.stream().map(PopularAlbum::id).distinct().count() != DailyPopularSelections.SIZE) {
                        return Duration.ofMinutes(1);
                    }
                    var now = ZonedDateTime.now(DailyPopularSelections.TIME_ZONE);
                    return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(DailyPopularSelections.TIME_ZONE));
                }, error -> Duration.ofMinutes(1), () -> Duration.ZERO);
    }

    private record Release(String name, String artist, LocalDate releaseDate, String cover) {}
    private record ConfirmedAlbum(Release release, List<String> images, long listeners, long plays, ObjectNode details) {}

    @GetMapping("/popular")
    public Mono<List<PopularAlbum>> popular() {
        return dailyAlbums;
    }

    private Mono<List<PopularAlbum>> loadDailyAlbums(LocalDate today) {
        return Mono.zip(releases("br", today), releases("us", today))
                .flatMapMany(markets -> {
                    var candidates = new LinkedHashMap<List<String>, Release>();
                    for (int index = 0; index < 8; index++) {
                        for (var market : List.of(markets.getT1(), markets.getT2())) {
                            if (index >= market.size()) continue;
                            var release = market.get(index);
                            candidates.putIfAbsent(albumKey(release.name(), release.artist()), release);
                        }
                    }
                    return Flux.fromIterable(candidates.values());
                })
                .flatMapSequential(this::confirmPopularity, 4)
                .sort(Comparator.comparingLong(ConfirmedAlbum::listeners).reversed()
                        .thenComparing(Comparator.comparingLong(ConfirmedAlbum::plays).reversed()))
                .distinct(album -> CatalogName.key(album.release().name()))
                .take(DailyPopularSelections.SIZE)
                .collectList()
                .filter(albums -> !albums.isEmpty())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ranking diário indisponível.")))
                .flatMap(albums -> Mono.fromCallable(() -> saveDailyAlbums(albums))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private List<PopularAlbum> saveDailyAlbums(List<ConfirmedAlbum> confirmed) {
        var ranked = new ArrayList<PopularAlbum>();
        for (var album : confirmed) {
            var release = album.release();
            var details = album.details().put("releasedate", release.releaseDate().toString());
            var id = catalog.importPopularAlbum(details);
            ranked.add(new PopularAlbum(id, release.name(), release.artist(), release.releaseDate(), album.images(),
                    album.listeners(), album.plays()));
        }
        dailySelections.saveAlbums(LocalDate.now(DailyPopularSelections.TIME_ZONE),
                ranked.stream().map(PopularAlbum::id).toList());
        return ranked;
    }

    private Mono<List<Release>> releases(String market, LocalDate today) {
        return readChart(market).flatMapMany(Flux::fromIterable).<Release>handle((item, sink) -> {
            String name = item.path("name").asText("").strip();
            String artist = item.path("artistName").asText("").strip();
            String date = item.path("releaseDate").asText("");
            if (name.isEmpty() || artist.isEmpty() || name.endsWith(" - Single")) return;
            try {
                var released = LocalDate.parse(date);
                if (!released.isBefore(today.minusDays(30)) && !released.isAfter(today)) {
                    sink.next(new Release(name, artist, released, item.path("artworkUrl100").asText("")));
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // Only include releases with a confirmed day.
            }
        }).take(8).collectList().onErrorReturn(List.of());
    }

    private Mono<ConfirmedAlbum> confirmPopularity(Release release) {
        return lastFm.discover(Map.of("method", "album.getInfo", "album", release.name(),
                        "artist", release.artist(), "preview", "true"))
                .flatMap(body -> Mono.fromCallable(() -> JSON.readTree(body).path("album")))
                .filter(album -> albumKey(album.path("name").asText(""), album.path("artist").asText(""))
                        .equals(albumKey(release.name(), release.artist())))
                .filter(album -> album.path("listeners").asLong() > 0)
                .map(album -> {
                    var images = new ArrayList<String>();
                    for (var image : album.path("image")) {
                        String url = image.path("#text").asText("");
                        if (!url.isBlank()) images.add(0, url);
                    }
                    if (!release.cover().isBlank()) images.add(release.cover());
                    var details = (ObjectNode) album;
                    if (!release.cover().isBlank()) {
                        if (!details.path("image").isArray()) details.putArray("image");
                        ((com.fasterxml.jackson.databind.node.ArrayNode) details.path("image")).insertObject(0)
                                .put("#text", release.cover());
                    }
                    return new ConfirmedAlbum(release, images, album.path("listeners").asLong(),
                            album.path("playcount").asLong(), details);
                })
                .onErrorResume(error -> Mono.empty());
    }

    private static List<String> albumKey(String title, String artist) {
        return List.of(title.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT),
                artist.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    @GetMapping(value = "/release-chart", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> chart(@RequestParam String market) {
        return readChart(market).map(Object::toString);
    }

    private Mono<JsonNode> readChart(String market) {
        if (!MARKETS.contains(market)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mercado inválido.");
        }
        String appleMusic = "https://rss.marketingtools.apple.com/api/v2/" + market + "/music/most-played/100/albums.json";
        String iTunes = "https://itunes.apple.com/" + market + "/rss/topalbums/limit=100/json";
        return readFeed(appleMusic, "results", Duration.ofSeconds(5))
                .onErrorResume(error -> readFeed(iTunes, "entry", Duration.ofSeconds(6)).map(this::normalizeITunes));
    }

    private Mono<JsonNode> readFeed(String url, String entries, Duration timeout) {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).build();
        return Mono.fromFuture(() -> charts.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .filter(response -> response.statusCode() == 200)
                .flatMap(response -> Mono.fromCallable(() -> JSON.readTree(response.body()).path("feed").path(entries)))
                .filter(result -> result.isArray() && !result.isEmpty())
                .timeout(timeout)
                .onErrorMap(error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ranking indisponível.", error))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ranking inválido.")));
    }

    private JsonNode normalizeITunes(JsonNode entries) {
        var albums = JSON.createArrayNode();
        for (var entry : entries) {
            var date = entry.path("im:releaseDate").path("label").asText("");
            if (date.length() < 10) continue;
            var album = albums.addObject();
            album.put("name", entry.path("im:name").path("label").asText(""));
            album.put("artistName", entry.path("im:artist").path("label").asText(""));
            album.put("releaseDate", date.substring(0, 10));
            var images = entry.path("im:image");
            album.put("artworkUrl100", images.path(images.size() - 1).path("label").asText(""));
            album.putArray("genres").addObject().put("name",
                    entry.path("category").path("attributes").path("label").asText(""));
        }
        return albums;
    }
}
