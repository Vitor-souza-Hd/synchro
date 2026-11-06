package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Discovery calls also retain viewed records in the local catalog. */
@RestController
@RequiredArgsConstructor
public class LastFmDiscoveryResource {
    private final WebClient webClient;
    private final LastFmConfig config;
    private final org.example.synchro.services.LastFmCatalogService catalog;
    private static final Set<String> METHODS = Set.of("artist.getinfo", "artist.gettoptracks",
            "artist.gettopalbums", "artist.gettoptags", "artist.search", "track.getinfo",
            "track.search", "album.getinfo", "album.search", "tag.gettopartists", "tag.gettopalbums");
    private static final Set<String> PARAMETERS = Set.of("artist", "track", "album", "tag", "mbid", "lang", "autocorrect");

    @GetMapping(value = "/last-fm/discovery", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> discover(@RequestParam Map<String, String> params) {
        String method = params.getOrDefault("method", "").toLowerCase(java.util.Locale.ROOT);
        if (!METHODS.contains(method)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta invalida");
        Map<String, String> values = new HashMap<>();
        for (String key : PARAMETERS) {
            String value = params.get(key);
            if (value != null) {
                if (value.length() > 250) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parametro muito longo");
                values.put(key, value);
            }
        }
        return webClient.get().uri(builder -> {
            builder.queryParam("method", method).queryParam("api_key", config.getApiKey())
                    .queryParam("format", "json").queryParam("limit", 30);
            values.forEach((key, value) -> builder.queryParam(key, "{" + key + "}"));
            return builder.build(values);
        }).retrieve().bodyToMono(String.class).timeout(Duration.ofSeconds(15))
                .flatMap(body -> Mono.fromRunnable(() -> catalog.persistDiscovery(method, body))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).thenReturn(body));
    }
}
