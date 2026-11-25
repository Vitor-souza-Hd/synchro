package org.example.synchro.resources;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.example.synchro.services.exception.LastFmApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/last-fm")
@RequiredArgsConstructor
@Slf4j
public class LastFmResource {
    private static final String PENDING_AUTH = LastFmResource.class.getName() + ".pendingAuth";
    private final LastfmService lastfmService;
    private final CookieService cookieService;

    @GetMapping(value = "/user/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getUserInfo(@PathVariable String username) {
        return lastfmService.getPublicUserInfo(username);
    }

    @GetMapping("/auth/login")
    public ResponseEntity<?> startAuth(
            @CookieValue(name = "token_jwt", required = false) String cookie, HttpServletRequest request) {
        if (!cookieService.checkCookie(cookie)) return unauthorized();
        String state = UUID.randomUUID().toString();
        String callback = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(request.getContextPath() + "/last-fm/auth/callback").replaceQuery(null)
                .queryParam("state", state).build().toUriString();
        try {
            String authUrl = lastfmService.getAuthUrl(callback);
            request.getSession(true).setAttribute(PENDING_AUTH,
                    new PendingAuth(state, cookieService.getId(cookie), Instant.now().plusSeconds(600)));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("authUrl", authUrl));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(503).body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/auth/callback")
    public Mono<ResponseEntity<Void>> callback(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String state, HttpServletRequest request) {
        if (!cookieService.checkCookie(cookie)) return Mono.just(profileRedirect("session_expired"));
        HttpSession browserSession = request.getSession(false);
        PendingAuth pending = null;
        if (browserSession != null) {
            synchronized (browserSession) {
                Object saved = browserSession.getAttribute(PENDING_AUTH);
                if (saved instanceof PendingAuth auth && auth.state().equals(state)
                        && auth.userId().equals(cookieService.getId(cookie)) && auth.expiresAt().isAfter(Instant.now())) {
                    pending = auth;
                    browserSession.removeAttribute(PENDING_AUTH);
                }
            }
        }
        if (pending == null) return Mono.just(profileRedirect("invalid_state"));
        if (token == null || token.isBlank()) return Mono.just(profileRedirect("cancelled"));
        Long userId = pending.userId();
        return lastfmService.connectAccount(userId, token)
                .then(Mono.defer(() -> lastfmService.syncProfile(userId)
                        .map(profile -> profileRedirect("connected"))
                        .onErrorReturn(profileRedirect("sync_pending"))))
                .onErrorResume(error -> {
                    int apiCode = error instanceof LastFmApiException apiError ? apiError.getCode() : -1;
                    // Do not log tokens, signatures, session keys or raw provider responses.
                    log.warn("Falha ao conectar Last.fm: userId={}, tipo={}, codigoLastFm={}",
                            userId, error.getClass().getSimpleName(), apiCode);
                    String result = switch (apiCode) {
                        case 4, 15 -> "token_expired";
                        case 14 -> "not_authorized";
                        case 10, 13, 26 -> "api_configuration";
                        case 11, 16, 29 -> "unavailable";
                        default -> "error";
                    };
                    return Mono.just(profileRedirect(result));
                });
    }

    @PostMapping("/sync")
    public Mono<ResponseEntity<?>> sync(@CookieValue(name = "token_jwt", required = false) String cookie) {
        if (!cookieService.checkCookie(cookie)) return Mono.just(unauthorized());
        return lastfmService.syncProfile(cookieService.getId(cookie))
                .<ResponseEntity<?>>map(profile -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile))
                .onErrorResume(error -> Mono.just(ResponseEntity.status(502).body(Map.of("error",
                        "Não foi possível atualizar o Last.fm agora. Seus dados salvos continuam disponíveis."))));
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> getMyLastFm(@CookieValue(name = "token_jwt", required = false) String cookie) {
        if (!cookieService.checkCookie(cookie)) return Mono.just(unauthorized());
        return lastfmService.getUserInfoBySynchroId(cookieService.getId(cookie))
                .<ResponseEntity<?>>map(json -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json))
                .onErrorResume(error -> Mono.just(ResponseEntity.badRequest().body(Map.of("error",
                        "Não foi possível consultar sua conta Last.fm."))));
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "Sua sessão expirou. Entre novamente no Synchro."));
    }

    private ResponseEntity<Void> profileRedirect(String result) {
        return ResponseEntity.status(303).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .location(URI.create("/frontend/pages/perfil.html?lastfm=" + result)).build();
    }

    private record PendingAuth(String state, Long userId, Instant expiresAt) implements java.io.Serializable {}

    @GetMapping(value = "/profiles/{userId}/top/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> getPublicTop(@PathVariable Long userId, @PathVariable String type,
                                                  @RequestParam(defaultValue = "7day") String period) {
        if (!validRanking(type, period)) return Mono.just(ResponseEntity.badRequest().build());

        return lastfmService.getTopBySynchroId(userId, type, period)
                .<ResponseEntity<?>>map(json -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .contentType(MediaType.APPLICATION_JSON).body(json))
                .onErrorResume(org.example.synchro.services.exception.ResourceNotFoundException.class,
                        error -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(org.example.synchro.services.exception.ResourceConflictException.class,
                        error -> Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(error -> Mono.just(ResponseEntity.status(502).build()));
    }

    @GetMapping(value = "/me/rankings/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> getPrivateRanking(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable String type,
            @RequestParam(defaultValue = "7day") String period,
            @RequestParam(defaultValue = "false") boolean refresh) {
        if (!cookieService.checkCookie(cookie)) return Mono.just(unauthorized());
        if (!validRanking(type, period)) return Mono.just(ResponseEntity.badRequest().build());

        Long userId = cookieService.getId(cookie);
        if (refresh) {
            return lastfmService.refreshSavedRanking(userId, type, period)
                    .<ResponseEntity<?>>map(json -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                            .contentType(MediaType.APPLICATION_JSON).body(json))
                    .onErrorResume(this::privateRankingFailure);
        }

        return lastfmService.findSavedRanking(userId, type, period)
                .<ResponseEntity<?>>map(saved -> {
                    if (saved.isEmpty()) return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
                    var ranking = saved.get();
                    boolean stale = ranking.updatedAt().plus(Duration.ofMinutes(30)).isBefore(Instant.now());
                    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                            .header("X-Synchro-Ranking-Stale", Boolean.toString(stale))
                            .contentType(MediaType.APPLICATION_JSON).body(ranking.payload());
                }).onErrorResume(this::privateRankingFailure);
    }

    private Mono<ResponseEntity<?>> privateRankingFailure(Throwable error) {
        int status = 502;
        if (error instanceof org.example.synchro.services.exception.ResourceConflictException) status = 409;
        else if (error instanceof org.example.synchro.services.exception.ResourceNotFoundException) status = 404;
        else if (error instanceof java.util.concurrent.TimeoutException) status = 504;
        return Mono.just(ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build());
    }

    @GetMapping(
            value = "/me/top/{type}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> getMyTop(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable String type,
            @RequestParam(defaultValue = "7day") String period) {
        if (!cookieService.checkCookie(cookie)) return Mono.just(unauthorized());
        if (!validRanking(type, period)) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Tipo ou período de ranking inválido.")));
        }

        return lastfmService.getTopBySynchroId(cookieService.getId(cookie), type, period)
                .<ResponseEntity<?>>map(json -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .contentType(MediaType.APPLICATION_JSON).body(json))
                .onErrorResume(java.util.concurrent.TimeoutException.class, error ->
                        Mono.just(ResponseEntity.status(504).cacheControl(CacheControl.noStore()).body(
                                Map.of("error", "O Last.fm demorou para responder. Tente novamente."))))
                .onErrorResume(org.example.synchro.services.exception.ResourceConflictException.class, error ->
                        Mono.just(ResponseEntity.status(409).body(Map.of("error", "Conecte sua conta Last.fm primeiro."))))
                .onErrorResume(error -> {
                    log.warn("Ranking Last.fm indisponível: tipo={}, período={}, falha={}", type, period,
                            error.getClass().getSimpleName());
                    return Mono.just(ResponseEntity.status(502).body(Map.of("error", "Falha ao consultar o Last.fm.")));
                });
    }

    // =========================================================
    // CONSULTAR TAGS DE UM ARTISTA
    // =========================================================

    private static boolean validRanking(String type, String period) {
        return java.util.Set.of("artists", "albums", "tracks").contains(type)
                && java.util.Set.of("overall", "7day", "1month", "3month", "6month", "12month").contains(period);
    }

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


}
