package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.ArtistPhotoService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalog/artists")
public class ArtistPhotoResource {
    private final ArtistPhotoService photos;

    @GetMapping("/{id}/photo")
    public Mono<ArtistPhotoService.Photo> photo(@PathVariable long id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return photos.find(id, refresh);
    }
}
