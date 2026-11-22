package org.example.synchro.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.example.synchro.services.RecomendacaoService;
import org.example.synchro.services.RecomendacaoService.Recommendations;
import org.example.synchro.services.RecomendacaoService.TopArtist;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    @GetMapping
    public Mono<ResponseEntity<Recommendations>> getRecommendations(
            @CookieValue(name = "token_jwt", required = false) String token) {
        if (!cookies.checkCookie(token)) throw new UnauthorizedException("Faça login para ver suas recomendações");
        Long userId = cookies.getId(token);

        return lastfm.getTopBySynchroId(userId, "artists", "overall")
                .flatMap(body -> Mono.fromCallable(() -> readArtists(body)))
                .onErrorResume(error -> Mono.empty())
                .filter(artists -> !artists.isEmpty())
                .flatMap(artists -> Mono.fromCallable(() -> recommendations.forTopArtists(userId, artists))
                        .subscribeOn(Schedulers.boundedElastic()))
                .switchIfEmpty(Mono.fromCallable(recommendations::fromCatalog).subscribeOn(Schedulers.boundedElastic()))
                .map(result -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result));
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
