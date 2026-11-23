
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.FavoriteArtistService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorites/artists")
@RequiredArgsConstructor
public class FavoriteArtistResource {

    private final FavoriteArtistService favoriteArtistService;

    public record ArtistRequest(String artist) {
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token
    ) {

        return ResponseEntity.ok(
                favoriteArtistService.listar(token)
        );
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<String>> publicArtists(@PathVariable long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(favoriteArtistService.publicArtistNames(userId));
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> verificar(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,

            @RequestParam String artist
    ) {

        return ResponseEntity.ok(
                favoriteArtistService.verificar(token, artist)
        );
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> adicionar(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,

            @RequestBody ArtistRequest request
    ) {

        return ResponseEntity.ok(
                favoriteArtistService.adicionar(
                        token,
                        request.artist()
                )
        );
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> remover(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,

            @RequestParam String artist
    ) {

        return ResponseEntity.ok(
                favoriteArtistService.remover(token, artist)
        );
    }
}
