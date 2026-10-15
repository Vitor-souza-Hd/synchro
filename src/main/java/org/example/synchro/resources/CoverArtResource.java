
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.CoverArtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/cover-art")
@RequiredArgsConstructor
public class CoverArtResource {

    private final CoverArtService coverArtService;

    // =========================================
    // BUSCAR CAPA PELO IDENTIFICADOR DO ÁLBUM
    // =========================================

    @GetMapping(
            value = "/release-group/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<Map<String, Object>> getAlbumCover(
            @PathVariable("id") String id
    ) {

        if (
                id == null ||
                        !id.matches(
                                "^[0-9a-fA-F]{8}-" +
                                        "[0-9a-fA-F]{4}-" +
                                        "[0-9a-fA-F]{4}-" +
                                        "[0-9a-fA-F]{4}-" +
                                        "[0-9a-fA-F]{12}$"
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador de álbum inválido."
            );
        }

        return coverArtService.getAlbumCover(id);
    }
}
