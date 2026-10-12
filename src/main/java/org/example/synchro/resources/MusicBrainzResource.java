
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.MusicBrainzService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/musicbrainz")
@RequiredArgsConstructor
public class MusicBrainzResource {

    private final MusicBrainzService musicBrainzService;

    // =========================================
    // PESQUISAR ÁLBUNS
    // =========================================

    @GetMapping(
            value = "/album/search",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<String> searchAlbums(
            @RequestParam("artist") String artist,
            @RequestParam("album") String album
    ) {

        if (
                artist == null ||
                        artist.isBlank() ||
                        album == null ||
                        album.isBlank()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe o artista e o álbum."
            );
        }

        return musicBrainzService.searchAlbums(artist, album);
    }
}
