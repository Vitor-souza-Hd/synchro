package org.example.synchro.resources;

import org.example.synchro.dto.MusicaDto;
import org.example.synchro.entities.Musica;
import org.example.synchro.services.MusicaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping(value = "/musica")
public class MusicaResource {

    @Autowired
    private MusicaService musicaService;

    @GetMapping(value = "{id}")
    public ResponseEntity<MusicaDto> buscarMusicaById(@PathVariable Long id){
        MusicaDto m1 = musicaService.findById(id);
        return ResponseEntity.ok().body(m1);
    }

}
