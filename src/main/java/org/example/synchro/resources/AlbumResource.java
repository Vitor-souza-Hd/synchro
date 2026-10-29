package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.AlbumDto;
import org.example.synchro.services.AlbumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/album")
public class AlbumResource {

    @Autowired
    private AlbumService albumService;

    @GetMapping("/id/{id}")
    public ResponseEntity<AlbumDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok().body(albumService.findById(id));
    }

    @GetMapping("/")
    public ResponseEntity<Set<AlbumDto>> findAll() {
        Set<AlbumDto> albumsDto = albumService.findAll();
        return ResponseEntity.ok().body(albumsDto);
    }

    @GetMapping("/{titulo}")
    public ResponseEntity<AlbumDto> findByTitulo(@PathVariable String titulo) {
        return ResponseEntity.ok().body(albumService.findByTitulo(titulo));
    }

    @GetMapping("/artista/{nome}")
    public ResponseEntity<List<AlbumDto>> findByArtistas(@PathVariable String nome) {
        return ResponseEntity.ok().body(albumService.findByArtista(nome));
    }
}