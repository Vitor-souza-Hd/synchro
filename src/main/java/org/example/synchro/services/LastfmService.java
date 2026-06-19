package org.example.synchro.services;


import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.lastFmDtos.LastFmUserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    public List<Integer> contadores (String username){
        LastFmUserDto lfu = getUserInfo(username)
                .block()
                .getBody();

        return Arrays.asList(Integer.parseInt(lfu.getUser().getPlaycount()),Integer.parseInt(lfu.getUser().getArtistCount()));
    };
 }

