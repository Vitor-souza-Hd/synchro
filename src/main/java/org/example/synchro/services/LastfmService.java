package org.example.synchro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.UserDto;
import org.example.synchro.dto.lastFmDtos.LastFmSessionDto;
import org.example.synchro.dto.lastFmDtos.LastFmUserDto;
import org.example.synchro.services.exception.LastFmApiException;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LastfmService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final WebClient webClient;
    private final LastFmConfig config;
    private final LastFmAccountStore accounts;

    public Mono<String> getUserInfo(String username) {
        return requestUserInfo(username).map(JsonNode::toString);
    }

    private Mono<JsonNode> requestUserInfo(String username) {
        return webClient.get()
                .uri(builder -> builder.queryParam("method", "user.getInfo")
                        .queryParam("user", "{username}").queryParam("api_key", config.getApiKey())
                        .queryParam("format", "json").build(username))
                .exchangeToMono(this::readResponse).timeout(TIMEOUT)
                .filter(node -> node.path("user").isObject() && !node.path("user").path("name").asText().isBlank())
                .switchIfEmpty(Mono.error(new IllegalStateException("O Last.fm não retornou os dados do perfil.")));
    }

    private Mono<LastFmUserDto> getUserInfoDto(String username) {
        return requestUserInfo(username).map(node -> convert(node, LastFmUserDto.class));
    }

    public List<Integer> contadores(String username) {
        var profile = getUserInfoDto(username).block();
        if (profile == null) throw new IllegalStateException("O Last.fm não retornou os dados do perfil.");
        return Arrays.asList(Integer.parseInt(profile.getUser().getPlaycount()),
                Integer.parseInt(profile.getUser().getArtistCount()));
    }

    public String getAuthUrl(String callbackUrl) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()
                || config.getSharedSecret() == null || config.getSharedSecret().isBlank()) {
            throw new IllegalStateException("A conexão com o Last.fm ainda não foi configurada.");
        }
        return UriComponentsBuilder.fromUriString("https://www.last.fm/api/auth/")
                .queryParam("api_key", "{key}").queryParam("cb", "{callback}")
                .encode().buildAndExpand(config.getApiKey(), callbackUrl).toUriString();
    }

    public Mono<Void> connectAccount(Long userId, String token) {
        // Last.fm tokens are opaque: real tokens include letters beyond hexadecimal.
        // Authorization is validated by Last.fm and the callback's browser-bound state.
        if (token == null || token.isBlank() || token.length() > 512) {
            return Mono.error(new IllegalArgumentException("Autorização Last.fm inválida. Tente conectar novamente."));
        }
        String signature = DigestUtils.md5DigestAsHex(("api_key" + config.getApiKey()
                + "methodauth.getSessiontoken" + token + config.getSharedSecret()).getBytes(StandardCharsets.UTF_8));
        return webClient.post().uri("")
                .body(BodyInserters.fromFormData("method", "auth.getSession")
                        .with("api_key", config.getApiKey()).with("token", token)
                        .with("api_sig", signature).with("format", "json"))
                .exchangeToMono(this::readResponse).timeout(TIMEOUT)
                .map(node -> convert(node, LastFmSessionDto.class))
                .switchIfEmpty(Mono.error(new IllegalStateException("O Last.fm não retornou a autorização.")))
                .flatMap(dto -> {
                    var session = dto.getSession();
                    if (session == null || session.getName() == null || session.getName().isBlank()
                            || session.getKey() == null || session.getKey().isBlank()) {
                        return Mono.error(new IllegalStateException("O Last.fm não retornou a autorização."));
                    }
                    // Run transactional JPA work away from the HTTP client's event loop.
                    return Mono.fromRunnable(() -> accounts.connect(userId, session))
                            .subscribeOn(Schedulers.boundedElastic()).then();
                });
    }

    public Mono<UserDto> syncProfile(Long userId) {
        return Mono.fromCallable(() -> accounts.connectedUsername(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(username -> getUserInfoDto(username)
                        .flatMap(dto -> Mono.fromCallable(() -> accounts.saveProfile(userId, username, dto.getUser()))
                                .subscribeOn(Schedulers.boundedElastic())));
    }

    public Mono<String> getUserInfoBySynchroId(Long userId) {
        return Mono.fromCallable(() -> accounts.connectedUsername(userId))
                .subscribeOn(Schedulers.boundedElastic()).flatMap(this::getUserInfo);
    }

    private Mono<JsonNode> readResponse(ClientResponse response) {
        // Last.fm also returns useful API error codes with HTTP 403/400 responses.
        return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
            JsonNode node = readResponse(body);
            if (response.statusCode().isError()) {
                throw new IllegalStateException("O Last.fm não concluiu a solicitação (HTTP "
                        + response.statusCode().value() + ").");
            }
            return node;
        });
    }

    private JsonNode readResponse(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            if (node != null && node.has("error")) {
                throw new LastFmApiException(node.path("error").asInt(-1));
            }
            if (node == null) {
                throw new IllegalStateException("O Last.fm não concluiu a solicitação. Tente novamente.");
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("O Last.fm retornou uma resposta inválida.", exception);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return JSON.treeToValue(node, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("O Last.fm retornou uma resposta inválida.", exception);
        }
    }
}
