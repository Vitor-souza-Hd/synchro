package org.example.synchro.services;


import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.lastFmDtos.LastFmSessionDto;
import org.example.synchro.dto.lastFmDtos.LastFmUserDto;
import org.example.synchro.entities.LastFmSession;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.LastFmSessionRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LastfmService {

    private final WebClient webClient;
    private final LastFmConfig config;
    private final CookieService cookieService;
    private final LastFmSessionRepository lastFmSessionRepository;
    private final UserRepository userRepository;

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

    public Mono<String> getToken(){
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("method", "auth.gettoken")
                        .queryParam("api_key",config.getApiKey())
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(json);
                        return node.path("token").asText();
                    } catch (Exception e) {
                        throw new RuntimeException("Erro ao extrair token", e);
                    }
                });
    }

    public String getAuthUrl(String token) {
        return "https://www.last.fm/api/auth/?api_key=" + config.getApiKey() + "&token=" + token;
    }

    public Mono<LastFmSessionDto> getSession(@CookieValue(name = "token_jwt", required = false) String cookie, String token) {
        try {
            String apiSig = generateApiSignature(token);
            if (!cookieService.checkCookie(cookie)) {
                throw new InvalidTokenException("sessão inválida");
            } else {
                return webClient
                        .post()
                        .uri("")
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .bodyValue("method=auth.getSession" +
                                "&api_key=" + config.getApiKey() +
                                "&token=" + token +
                                "&api_sig=" + apiSig +
                                "&format=json")
                        .retrieve()
                        .bodyToMono(LastFmSessionDto.class)
                        .doOnSuccess(LastFmSessionDto -> {
                            Long id = Long.parseLong(cookieService.getId(cookie));
                            LastFmSession lastFmSession = new LastFmSession(LastFmSessionDto);
                            Optional<User> user = userRepository.findById(id);
                            user.ifPresent(user1 -> {
                                if (user1.getId() == null) {
                                    throw new InvalidTokenException("sessão inválida");
                                } else {
                                    lastFmSessionRepository.save(lastFmSession);
                                    lastFmSession.setSynchroUser(user1);
                                    userRepository.save(user1);
                                    lastFmSessionRepository.save(lastFmSession);
                                }
                            });

                        });
            }
        } catch (InvalidTokenException e1) {
            System.out.println(e1.getLocalizedMessage());
            throw e1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private String generateApiSignature(String token) {
        String toSign = "api_key" + config.getApiKey() +
                "methodauth.getSession" +
                "token" + token +
                config.getSharedSecret();

        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(toSign.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar api_sig", e);
        }
    }
}