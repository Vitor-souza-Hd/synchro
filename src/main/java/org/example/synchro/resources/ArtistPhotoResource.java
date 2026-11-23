package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.ArtistPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/catalog/artists")
public class ArtistPhotoResource {
    private final ArtistPhotoService photos;

    @GetMapping("/by-name/photo")
    public Mono<ArtistPhotoService.Photo> photoByName(@RequestParam String name,
            @RequestParam(defaultValue = "false") boolean refresh) {
        String artist = name.strip();
        if (artist.isEmpty() || artist.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de artista inválido.");
        }
        return photos.findByName(artist, refresh);
    }

    @GetMapping("/{id}/photo")
    public Mono<ArtistPhotoService.Photo> photo(@PathVariable long id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return photos.find(id, refresh);
    }
}
