package org.example.synchro.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.example.synchro.services.RecomendacaoService;
import org.example.synchro.services.RecomendacaoService.Recommendations;
import org.example.synchro.services.RecomendacaoService.RecentRecommendations;
import org.example.synchro.services.RecomendacaoService.TopArtist;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/recomendacoes")
@RequiredArgsConstructor
public class RecomendacaoResource {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CookieService cookies;
    private final LastfmService lastfm;
    private final RecomendacaoService recommendations;
    private final AlbumReleaseResource releases;

    @GetMapping
    public Mono<ResponseEntity<Recommendations>> getRecommendations(
            @CookieValue(name = "token_jwt", required = false) String token,
            @RequestParam(name = "limit", defaultValue = "6") int requestedLimit) {
        if (!cookies.checkCookie(token)) throw new UnauthorizedException("Faça login para ver suas recomendações");
        Long userId = cookies.getId(token);
        int limit = Math.max(1, Math.min(requestedLimit, RecomendacaoService.MAX_SUGGESTIONS));

        return Mono.zip(topArtists(userId, "1month"), topArtists(userId, "overall"))
                .flatMap(rankings -> Mono.fromCallable(() -> recommendations.recommend(userId, rankings.getT1(), rankings.getT2(), limit))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(result -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result));
    }

    @GetMapping("/lancamentos")
    public Mono<ResponseEntity<RecentRecommendations>> getRecentRecommendations(
            @CookieValue(name = "token_jwt", required = false) String token) {
        Long userId = cookies.checkCookie(token) ? cookies.getId(token) : null;
        return Mono.zip(releases.recentMusic(), recentTastes(userId, "1month"), recentTastes(userId, "overall"))
                .flatMap(data -> Mono.fromCallable(() -> recommendations.recommendRecent(
                                userId, data.getT2(), data.getT3(), data.getT1()))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(result -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result));
    }

    private Mono<List<TopArtist>> recentTastes(Long userId, String period) {
        if (userId == null) return Mono.just(List.of());
        return topArtists(userId, period).timeout(Duration.ofSeconds(4), Mono.just(List.of()));
    }

    private Mono<List<TopArtist>> topArtists(Long userId, String period) {
        return lastfm.getTopBySynchroId(userId, "artists", period)
                .flatMap(body -> Mono.fromCallable(() -> readArtists(body)))
                .onErrorReturn(List.of())
                .defaultIfEmpty(List.of());
    }

    private List<TopArtist> readArtists(String body) throws java.io.IOException {
        List<TopArtist> artists = new ArrayList<>();
        for (JsonNode artist : JSON.readTree(body).path("topartists").path("artist")) {
            String name = artist.path("name").asText("").strip();
            if (!name.isEmpty()) artists.add(new TopArtist(name, artistImage(artist.path("image"))));
        }
        return artists;
    }

    private String artistImage(JsonNode images) {
        String extraLarge = null;
        for (JsonNode image : images) {
            String url = image.path("#text").asText("");
            if (url.isBlank()) continue;
            String size = image.path("size").asText();
            if ("mega".equals(size)) return url;
            if ("extralarge".equals(size)) extraLarge = url;
        }
        return extraLarge;
    }
}
