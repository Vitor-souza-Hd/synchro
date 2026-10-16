package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.MusicaDto;
import org.example.synchro.dto.Musica_AlbumDto;
import org.example.synchro.services.MusicaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/musica")
@RequiredArgsConstructor
public class MusicaResource {

    private final MusicaService musicaService;

    // Lista todas as músicas (usado na home)
    @GetMapping(value = "/")
    public ResponseEntity<List<Musica_AlbumDto>> findAll() {
        return ResponseEntity.ok().body(musicaService.findAll());
    }

    @GetMapping(value = "/{titulo}")
    public ResponseEntity<MusicaDto> findByTitulo(@PathVariable String titulo) {
        return ResponseEntity.ok().body(musicaService.findByTitulo(titulo));
    }

    @GetMapping(value = "/artista/{artista}")
    public ResponseEntity<List<MusicaDto>> findByArtista(@PathVariable String artista) {
        return ResponseEntity.ok().body(musicaService.findByArtistas(artista));
    }

    @GetMapping(value = "/buscar")
    public ResponseEntity<List<MusicaDto>> buscarParcial(@RequestParam("query") String query) {
        return ResponseEntity.ok().body(musicaService.findByTermoGeral(query));
    }
}