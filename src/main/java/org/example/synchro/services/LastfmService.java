package org.example.synchro.services;


import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.lastFmDtos.LastFmUserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LastfmService {

    private final WebClient webClient;
    private final LastFmConfig config;

    public Mono<ResponseEntity<LastFmUserDto>> getUserInfo(String username){
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("method", "user.getinfo")
                        .queryParam("user", username)
                        .queryParam("api_key", config.getApiKey())
                        .queryParam("format","json")
                        .build())
                .retrieve()
                .toEntity(LastFmUserDto.class)
                .timeout(Duration.ofSeconds(15));
    }
}
