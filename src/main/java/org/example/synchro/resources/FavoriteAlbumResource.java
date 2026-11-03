
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.FavoriteAlbumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/favorites/albums")
@RequiredArgsConstructor
public class FavoriteAlbumResource {

    private final FavoriteAlbumService service;

    public record AlbumRequest(
            String releaseGroupId,
            String album,
            String artist
    ) {}

    @GetMapping
    public ResponseEntity<
            List<FavoriteAlbumService.AlbumResponse>
            > listar(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token
    ) {
        return ResponseEntity.ok(
                service.listar(token)
        );
    }

    @GetMapping("/check")
    public ResponseEntity<
            FavoriteAlbumService.FavoriteStatus
            > verificar(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,

            @RequestParam String releaseGroupId
    ) {
        return ResponseEntity.ok(
                service.verificar(token, releaseGroupId)
        );
    }

    @PostMapping
    public ResponseEntity<
            FavoriteAlbumService.AlbumResponse
            > adicionar(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,

            @RequestBody AlbumRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                service.adicionar(
                        token,
                        request.releaseGroupId(),
                        request.album(),
                        request.artist()
                )
        );
    }

    @DeleteMapping
    public ResponseEntity<
            FavoriteAlbumService.FavoriteStatus
            > remover(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,

            @RequestParam String releaseGroupId
    ) {
        return ResponseEntity.ok(
                service.remover(token, releaseGroupId)
        );
    }
}
