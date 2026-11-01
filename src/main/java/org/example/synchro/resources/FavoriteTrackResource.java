
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.FavoriteTrackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/favorites/tracks")
@RequiredArgsConstructor
public class FavoriteTrackResource {

    private final FavoriteTrackService service;

    public record TrackRequest(
            String track,
            String artist
    ) {
    }

    @GetMapping
    public ResponseEntity<List<FavoriteTrackService.TrackResponse>> listar(
            @CookieValue(name = "token_jwt", required = false)
            String token
    ) {
        return ResponseEntity.ok(service.listar(token));
    }

    @GetMapping("/check")
    public ResponseEntity<FavoriteTrackService.FavoriteStatus> verificar(
            @CookieValue(name = "token_jwt", required = false)
            String token,
            @RequestParam String track,
            @RequestParam String artist
    ) {
        return ResponseEntity.ok(
                service.verificar(token, track, artist)
        );
    }

    @PostMapping
    public ResponseEntity<FavoriteTrackService.TrackResponse> adicionar(
            @CookieValue(name = "token_jwt", required = false)
            String token,
            @RequestBody TrackRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                service.adicionar(
                        token,
                        request.track(),
                        request.artist()
                )
        );
    }

    @DeleteMapping
    public ResponseEntity<FavoriteTrackService.FavoriteStatus> remover(
            @CookieValue(name = "token_jwt", required = false)
            String token,
            @RequestParam String track,
            @RequestParam String artist
    ) {
        return ResponseEntity.ok(
                service.remover(token, track, artist)
        );
    }
}
