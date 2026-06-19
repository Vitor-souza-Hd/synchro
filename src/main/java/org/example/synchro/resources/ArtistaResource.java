package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.ArtistaDto;
import org.example.synchro.services.ArtistaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/artista")
@RequiredArgsConstructor
public class ArtistaResource {

    private final ArtistaService artistaService;

    @GetMapping(value = "/{nome}")
    public ResponseEntity<ArtistaDto> findByNome (@PathVariable String nome) {
        return ResponseEntity.ok().body(artistaService.findByNomeDto(nome));
    }
}
