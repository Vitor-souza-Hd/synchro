
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.MusicBrainzService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

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

        validarParametros(artist, album);

        return musicBrainzService.searchAlbums(artist, album);
    }

    // =========================================
    // PESQUISAR GRAVAÇÕES
    // =========================================

    @GetMapping(
            value = "/track/search",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<String> searchTracks(
            @RequestParam("artist") String artist,
            @RequestParam("track") String track
    ) {

        validarParametros(artist, track);

        return musicBrainzService.searchTracks(artist, track);
    }

    // =========================================
    // IDENTIFICAR ÁLBUM DA MÚSICA
    // =========================================

    @GetMapping(
            value = "/track/album",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<Map<String, Object>> findTrackAlbum(
            @RequestParam("artist") String artist,
            @RequestParam("track") String track
    ) {

        validarParametros(artist, track);

        return musicBrainzService.findTrackAlbum(
                artist,
                track
        );
    }

    // =========================================
    // VALIDAR PARÂMETROS
    // =========================================

    private void validarParametros(
            String primeiro,
            String segundo
    ) {

        if (
                primeiro == null ||
                        primeiro.isBlank() ||
                        segundo == null ||
                        segundo.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe os parâmetros obrigatórios."
            );
        }
    }
}
