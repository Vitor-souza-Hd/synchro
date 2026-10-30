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
        return lastfmService.getUserInfo(username);
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
                .location(URI.create("/frontend/pages/user.html?lastfm=" + result)).build();
    }

    private record PendingAuth(String state, Long userId, Instant expiresAt) implements java.io.Serializable {}
}
