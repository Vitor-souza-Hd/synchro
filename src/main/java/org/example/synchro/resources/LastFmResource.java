package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.lastFmDtos.LastFmUserDto;
import org.example.synchro.services.LastfmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/last-fm")
@RequiredArgsConstructor
public class LastFmResource {

    @Autowired
    LastfmService lastfmService;
    @Autowired
    WebClient webClient;
    @Autowired
    LastFmConfig config;

    @GetMapping(value = "/user/{username}")
    public Mono<ResponseEntity<LastFmUserDto>> getuserInfo(@PathVariable String username) {
        return lastfmService.getUserInfo(username);
    }
    @GetMapping("/users/{username}")
    public Mono<String> getusersInfo(@PathVariable String username) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("method", "user.getinfo")
                        .queryParam("user", username)
                        .queryParam("api_key", config.getApiKey())
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(System.out::println);
    }
}
