
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/last-fm")
@RequiredArgsConstructor
public class LastFmResource {

    private final LastfmService lastfmService;
    private final CookieService cookieService;
    private final LastFmConfig config;

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
        return lastfmService.getUserInfo(username);
    }

    // =========================================================
    // INICIAR AUTENTICAÇÃO WEB LAST.FM
    // =========================================================

    @GetMapping("/auth/login")
    public ResponseEntity<Map<String, String>> startAuth() {

        String authUrl =
                "https://www.last.fm/api/auth/?api_key="
                        + config.getApiKey()
                        + "&cb=http%3A%2F%2Flocalhost%3A8080%2Flast-fm%2Fauth%2Fcallback";

        return ResponseEntity.ok(
                Map.of("authUrl", authUrl)
        );
    }

    // =========================================================
    // CALLBACK LAST.FM
    // =========================================================

    @GetMapping("/auth/callback")
    public Mono<ResponseEntity<Void>> callback(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String cookie,

            @RequestParam String token
    ) {

        if (!cookieService.checkCookie(cookie)) {
            return Mono.just(
                    ResponseEntity
                            .status(401)
                            .<Void>build()
            );
        }

        return lastfmService
                .getSession(cookie, token)
                .map(sessionDto ->
                        ResponseEntity
                                .status(302)
                                .location(
                                        URI.create(
                                                "/TCC/TCC/SYNCHRO/SITE/user.html"
                                        )
                                )
                                .<Void>build()
                )
                .onErrorResume(error -> {
                    System.err.println(
                            "Erro na autenticação Last.fm: "
                                    + error.getMessage()
                    );

                    return Mono.just(
                            ResponseEntity
                                    .badRequest()
                                    .<Void>build()
                    );
                });
    }

    // =========================================================
    // LAST.FM DO USUÁRIO SYNCHRO LOGADO
    // =========================================================

    @GetMapping(
            value = "/me",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> getMyLastFm(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String cookie
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

        Long userId = cookieService.getId(cookie);

        return lastfmService
                .getUserInfoBySynchroId(userId)
                .map(json ->
                        ResponseEntity
                                .ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(json)
                )
                .onErrorResume(error ->
                        Mono.just(
                                ResponseEntity
                                        .badRequest()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(
                                                "{\"error\":\""
                                                        + escapeJson(error.getMessage())
                                                        + "\"}"
                                        )
                        )
                );
    }

    // =========================================================
    // RANKINGS DO USUARIO SYNCHRO AUTENTICADO
    // =========================================================

    @GetMapping(
            value = "/me/top/{type}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> getMyTop(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String cookie,

            @PathVariable String type,

            @RequestParam(
                    defaultValue = "7day"
            ) String period
    ) {

        // Valida o login Synchro.
        if (!cookieService.checkCookie(cookie)) {
            return Mono.just(
                    ResponseEntity
                            .status(401)
                            .body(
                                    "{\"error\":\"Sessao Synchro invalida\"}"
                            )
            );
        }

        // Valida antes de chamar o servico.
        if (!java.util.Set.of(
                "artists",
                "albums",
                "tracks"
        ).contains(type)) {

            return Mono.just(
                    ResponseEntity
                            .badRequest()
                            .body(
                                    "{\"error\":\"Tipo de ranking invalido\"}"
                            )
            );
        }

        if (!java.util.Set.of(
                "overall",
                "7day",
                "1month",
                "3month",
                "6month",
                "12month"
        ).contains(period)) {

            return Mono.just(
                    ResponseEntity
                            .badRequest()
                            .body(
                                    "{\"error\":\"Periodo invalido\"}"
                            )
            );
        }

        Long userId;

        try {
            userId = cookieService.getId(cookie);
        } catch (RuntimeException error) {
            return Mono.just(
                    ResponseEntity
                            .status(401)
                            .body(
                                    "{\"error\":\"Sessao Synchro invalida\"}"
                            )
            );
        }

        return lastfmService
                .getTopBySynchroId(
                        userId,
                        type,
                        period
                )
                .map(json ->
                        ResponseEntity
                                .ok()
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .body(json)
                )
                .onErrorResume(
                        IllegalStateException.class,
                        error -> Mono.just(
                                ResponseEntity
                                        .status(409)
                                        .body(
                                                "{\"error\":\"Conta Last.fm nao conectada\"}"
                                        )
                        )
                )
                .onErrorResume(
                        IllegalArgumentException.class,
                        error -> Mono.just(
                                ResponseEntity
                                        .badRequest()
                                        .body(
                                                "{\"error\":\"Consulta invalida\"}"
                                        )
                        )
                )
                .onErrorResume(
                        error -> {
                            System.err.println(
                                    "Erro ao consultar ranking Last.fm: "
                                            + error.getMessage()
                            );

                            return Mono.just(
                                    ResponseEntity
                                            .status(502)
                                            .body(
                                                    "{\"error\":\"Falha ao consultar Last.fm\"}"
                                            )
                            );
                        }
                );
    }

    // =========================================================
    // CONSULTAR TAGS DE UM ARTISTA
    // =========================================================

    @GetMapping(
            value = "/artist/tags",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> getArtistTags(
            @RequestParam String artist
    ) {

        if (artist == null
                || artist.isBlank()
                || artist.length() > 200) {

            return Mono.just(
                    ResponseEntity
                            .badRequest()
                            .body(
                                    "{\"error\":\"Nome do artista invalido\"}"
                            )
            );
        }

        return lastfmService
                .getArtistTopTags(artist)
                .map(json ->
                        ResponseEntity
                                .ok()
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .body(json)
                )
                .onErrorResume(error -> {
                    System.err.println(
                            "Erro ao consultar tags do artista: "
                                    + error.getMessage()
                    );

                    return Mono.just(
                            ResponseEntity
                                    .status(502)
                                    .body(
                                            "{\"error\":\"Falha ao consultar tags no Last.fm\"}"
                                    )
                    );
                });
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
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
