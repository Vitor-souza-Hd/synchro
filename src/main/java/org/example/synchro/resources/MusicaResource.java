package org.example.synchro.resources;

import org.example.synchro.dto.MusicaDto;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.services.MusicaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(value = "/musica")
public class MusicaResource {

    @Autowired
    private MusicaService musicaService;

    @GetMapping(value = "/{titulo}")
    public ResponseEntity<MusicaDto> findByTitulo(@PathVariable String titulo){
        return ResponseEntity.ok().body(musicaService.findByTitulo(titulo));
    }

    @GetMapping(value = "artista/{artista}")
    public ResponseEntity<List<MusicaDto>> findByArtista(@PathVariable String artista){
        return ResponseEntity.ok().body(musicaService.findByArtistas(artista));
    }

}
