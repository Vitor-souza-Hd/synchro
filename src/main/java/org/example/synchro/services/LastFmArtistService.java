
package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LastFmArtistService {

    private final WebClient webClient;
    private final LastFmConfig config;

    // PESQUISAR ARTISTAS PELO LAST.FM
    public Mono<String> searchArtists(String query) {

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("method", "artist.search")
                        .queryParam("artist", query)
                        .queryParam("api_key", config.getApiKey())
                        .queryParam("format", "json")
                        .queryParam("limit", 10)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15));
    }

    // PESQUISAR MÚSICAS PELO LAST.FM
    public Mono<String> searchTracks(String query) {

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("method", "track.search")
                        .queryParam("track", query)
                        .queryParam("api_key", config.getApiKey())
                        .queryParam("format", "json")
                        .queryParam("limit", 10)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15));
    }
}
