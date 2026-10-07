package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/last-fm")
@RequiredArgsConstructor
public class LastFmResource {

    private final LastfmService lastfmService;
    private final LastFmConfig config;
    private final CookieService cookieService;


    // =========================================================
    // BUSCAR USUÁRIO LAST.FM
    // =========================================================

    @GetMapping(
            value = "/user/{username}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<String> getUserInfo(
            @PathVariable String username
    ) {

        return lastfmService
                .getUserInfo(username);
    }


    // =========================================================
    // INICIAR AUTENTICAÇÃO LAST.FM
    // =========================================================

    @GetMapping("/auth/login")
    public Mono<ResponseEntity<Map<String, String>>>
    startAuth() {

        return lastfmService
                .getToken()
                .map(token ->
                        ResponseEntity.ok(
                                Map.of(
                                        "authUrl",
                                        "https://www.last.fm/api/auth/?api_key="
                                                + config.getApiKey()
                                                + "&token="
                                                + token,

                                        "token",
                                        token
                                )
                        )
                );
    }


    // =========================================================
    // CALLBACK LAST.FM
    // =========================================================

    @GetMapping("/auth/callback")
    public Mono<ResponseEntity<Map<String, String>>>
    callback(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            )
            String cookie,

            @RequestParam String token
    ) {

        return lastfmService
                .getSession(
                        cookie,
                        token
                )
                .map(sessionDto -> {

                    String sessionKey =
                            sessionDto
                                    .getSession()
                                    .getKey();

                    return ResponseEntity.ok(
                            Map.of(
                                    "message",
                                    "Autenticação realizada com sucesso!",

                                    "username",
                                    sessionDto
                                            .getSession()
                                            .getName(),

                                    "sessionKey",
                                    sessionKey,

                                    "token",
                                    token
                            )
                    );
                })
                .onErrorResume(e ->
                        Mono.just(
                                ResponseEntity
                                        .badRequest()
                                        .body(
                                                Map.of(
                                                        "error",
                                                        e.getMessage()
                                                )
                                        )
                        )
                );
    }


    // =========================================================
    // LAST.FM DO USUÁRIO SYNCHRO LOGADO
    // =========================================================

    @GetMapping(
            value = "/me",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>>
    getMyLastFm(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            )
            String cookie
    ) {

        if (!cookieService.checkCookie(cookie)) {

            return Mono.just(
                    ResponseEntity
                            .status(401)
                            .body(
                                    "{\"error\":\"Sessão Synchro inválida\"}"
                            )
            );
        }

        Long userId =
                cookieService.getId(cookie);

        return lastfmService
                .getUserInfoBySynchroId(userId)
                .map(json ->
                        ResponseEntity
                                .ok()
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .body(json)
                )
                .onErrorResume(e ->
                        Mono.just(
                                ResponseEntity
                                        .badRequest()
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .body(
                                                "{\"error\":\""
                                                        + escapeJson(
                                                        e.getMessage()
                                                )
                                                        + "\"}"
                                        )
                        )
                );
    }


    // =========================================================
    // ESCAPAR TEXTO PARA JSON
    // =========================================================

    private String escapeJson(String text) {

        if (text == null) {
            return "Erro desconhecido";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}