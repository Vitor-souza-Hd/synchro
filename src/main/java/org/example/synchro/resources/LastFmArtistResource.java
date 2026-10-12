
package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.LastFmArtistService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/last-fm")
@RequiredArgsConstructor
public class LastFmArtistResource {

    private final LastFmArtistService artistService;

    // PESQUISAR ARTISTAS
    @GetMapping(
            value = "/artist/search",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<String> searchArtists(
            @RequestParam("q") String query
    ) {
        return artistService.searchArtists(query);
    }

    // PESQUISAR MÚSICAS
    @GetMapping(
            value = "/track/search",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<String> searchTracks(
            @RequestParam("q") String query
    ) {
        return artistService.searchTracks(query);
    }
}
