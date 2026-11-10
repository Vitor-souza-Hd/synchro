package org.example.synchro.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.services.LastFmCatalogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Discovery calls also retain viewed records in the local catalog. */
@RestController
@RequiredArgsConstructor
public class LastFmDiscoveryResource {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_LOOKUPS = 256;
    private static final long MAX_CACHE_SECONDS = 300;
    private static final long DEFAULT_COOLDOWN_SECONDS = 15;
    private static final Set<String> METHODS = Set.of("artist.getinfo", "artist.gettoptracks",
            "artist.gettopalbums", "artist.gettoptags", "artist.search", "track.getinfo",
            "track.search", "album.getinfo", "album.search", "tag.gettopartists", "tag.gettopalbums");
    private static final Set<String> PARAMETERS = Set.of("artist", "track", "album", "tag", "mbid", "lang", "autocorrect");

    private final WebClient webClient;
    private final LastFmConfig config;
    private final LastFmCatalogService catalog;
    private final Map<Lookup, Entry> lookups = new LinkedHashMap<>(16, .75f, true);
    private final AtomicReference<Instant> pausedUntil = new AtomicReference<>(Instant.EPOCH);

    private record Lookup(String method, Map<String, String> values) {}
    private record Result(String body, long cacheSeconds, boolean valid) {}
    private static final class Entry {
        private volatile Instant expires = Instant.MAX;
        private Mono<String> result;
    }

    @GetMapping(value = "/last-fm/discovery", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> discover(@RequestParam Map<String, String> params) {
        String method = params.getOrDefault("method", "").toLowerCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta invalida");
        Map<String, String> values = new HashMap<>();
        for (String key : PARAMETERS) {
            String value = params.get(key);
            if (value != null) {
                if (value.length() > 250) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parametro muito longo");
                values.put(key, value);
            }
        }
        var lookup = new Lookup(method, Map.copyOf(values));
        return Mono.defer(() -> cached(lookup));
    }

    private synchronized Mono<String> cached(Lookup lookup) {
        Instant now = Instant.now();
        var existing = lookups.get(lookup);
        if (existing != null && now.isBefore(existing.expires)) return existing.result;

        lookups.remove(lookup);
        Instant until = pausedUntil.get();
        if (now.isBefore(until)) return Mono.error(rateLimited(until));
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Last.fm não configurado."));
        }
        if (lookups.size() >= MAX_LOOKUPS) {
            lookups.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expires));
            if (lookups.size() >= MAX_LOOKUPS) {
                var completed = lookups.entrySet().stream().filter(entry -> entry.getValue().expires != Instant.MAX).findFirst();
                completed.ifPresent(entry -> lookups.remove(entry.getKey()));
            }
            if (lookups.size() >= MAX_LOOKUPS) {
                return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Muitas consultas em andamento."));
            }
        }

        var entry = new Entry();
        entry.result = request(lookup).flatMap(result -> {
            if (!result.valid()) return Mono.just(result);
            return Mono.fromRunnable(() -> catalog.persistDiscovery(lookup.method(), result.body()))
                    .subscribeOn(Schedulers.boundedElastic()).thenReturn(result);
        }).doOnNext(result -> entry.expires = Instant.now().plusSeconds(result.cacheSeconds()))
                .doOnError(error -> entry.expires = Instant.EPOCH)
                .map(Result::body).cache();
        lookups.put(lookup, entry);
        return entry.result;
    }

    private Mono<Result> request(Lookup lookup) {
        return webClient.get().uri(builder -> {
            builder.queryParam("method", lookup.method()).queryParam("api_key", config.getApiKey())
                    .queryParam("format", "json").queryParam("limit", 30);
            lookup.values().forEach((key, value) -> builder.queryParam(key, "{" + key + "}"));
            return builder.build(lookup.values());
        }).exchangeToMono(this::read).timeout(Duration.ofSeconds(15));
    }

    private Mono<Result> read(ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();
        if (response.statusCode().value() == 429) {
            var error = pause(headers);
            return response.releaseBody().then(Mono.error(error));
        }
        return response.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta vazia do Last.fm.")))
                .map(body -> {
                    try {
                        var data = JSON.readTree(body);
                        if (data.path("error").asInt() == 29) throw pause(headers);
                        if (!response.statusCode().is2xxSuccessful()) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Last.fm indisponível.");
                        }
                        boolean valid = data.isObject() && !data.has("error");
                        return new Result(body, valid ? cacheSeconds(headers) : 0, valid);
                    } catch (JsonProcessingException error) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida do Last.fm.");
                    }
                });
    }

    private long cacheSeconds(HttpHeaders headers) {
        if (headers.getVary().contains("*")) return 0;
        String cache = headers.getCacheControl();
        long maxAge = -1;
        long sharedMaxAge = -1;
        if (cache != null) {
            for (String raw : cache.toLowerCase(Locale.ROOT).split(",")) {
                var directive = raw.trim().split("=", 2);
                switch (directive[0].trim()) {
                    case "private", "no-store", "no-cache" -> { return 0; }
                    case "max-age" -> maxAge = seconds(directive);
                    case "s-maxage" -> sharedMaxAge = seconds(directive);
                    default -> { }
                }
            }
        }
        long lifetime = sharedMaxAge >= 0 ? sharedMaxAge : maxAge;
        if (lifetime < 0 && headers.getExpires() >= 0) {
            long date = headers.getDate();
            lifetime = (headers.getExpires() - (date >= 0 ? date : System.currentTimeMillis())) / 1000;
        }
        long age = seconds(new String[]{"age", headers.getFirst(HttpHeaders.AGE)});
        return lifetime <= 0 ? 0 : Math.max(0, Math.min(MAX_CACHE_SECONDS, lifetime - Math.max(0, age)));
    }

    private long seconds(String[] directive) {
        if (directive.length < 2 || directive[1] == null) return -1;
        try { return Long.parseLong(directive[1].trim().replace("\"", "")); }
        catch (NumberFormatException error) { return -1; }
    }

    private ResponseStatusException pause(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        Instant now = Instant.now();
        long wait = seconds(new String[]{"retry-after", value});
        Instant deadline = now.plusSeconds(wait < 0 ? DEFAULT_COOLDOWN_SECONDS : Math.min(86400, Math.max(1, wait)));
        if (wait < 0 && value != null) {
            try {
                Instant parsed = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                if (parsed.isAfter(now)) deadline = parsed;
            } catch (java.time.format.DateTimeParseException ignored) { }
        }
        Instant until = pausedUntil.accumulateAndGet(deadline, (current, next) -> current.isAfter(next) ? current : next);
        return rateLimited(until);
    }

    private ResponseStatusException rateLimited(Instant until) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Last.fm limitou temporariamente as consultas.") {
            @Override public HttpHeaders getHeaders() {
                var headers = new HttpHeaders();
                headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, Duration.between(Instant.now(), until).toSeconds() + 1)));
                headers.setCacheControl("no-store");
                return headers;
            }
        };
    }
}
