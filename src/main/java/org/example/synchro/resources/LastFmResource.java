package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.lastFmDtos.LastFmUserDto;
import org.example.synchro.services.LastfmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping(value = "/last-fm")
@RequiredArgsConstructor
public class LastFmResource {

    private final LastfmService lastfmService;

    private final WebClient webClient;

    private final LastFmConfig config;

    @GetMapping(value = "/user/{username}")
    public Mono<ResponseEntity<LastFmUserDto>> getuserInfo(@PathVariable String username) {
        return lastfmService.getUserInfo(username);
    }

    @GetMapping("/auth/login")
    public Mono<ResponseEntity<Map<String, String>>> startAuth() {
        return lastfmService.getToken()
                .map(token -> ResponseEntity.ok(Map.of(
                        "authUrl", "https://www.last.fm/api/auth/?api_key=" + config.getApiKey() + "&token=" + token,
                        "token", token
                )));
    }
    @GetMapping("/auth/callback")
    public Mono<ResponseEntity<Map<String, String>>> callback(@RequestParam String token) {
        return lastfmService.getSession(token)
                .map(sessionDto -> {
                    String sessionKey = sessionDto.getSession().getKey();
                    
                    return ResponseEntity.ok(Map.of(
                            "message", "Autenticação realizada com sucesso!",
                            "username", sessionDto.getSession().getName(),
                            "sessionKey", sessionKey,
                            "token", token
                    ));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest()
                        .body(Map.of("error", e.getMessage()))));
    }
    @GetMapping("/auth/status")
    public ResponseEntity<String> status() {
        // Aqui você pode verificar se tem session key salva no banco
        return ResponseEntity.ok("Endpoint de status - implemente conforme sua lógica");
    }
}
