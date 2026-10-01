package org.example.synchro.resources;

import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SugestoesResource {

    @Autowired
    private MusicaRepository musicaRepository;

    @Autowired
    private ArtistaRepository artistaRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @GetMapping("/sugestoes")
    public List<String> sugestoes(@RequestParam(name = "q", defaultValue = "") String query) {
        List<String> resultado = new ArrayList<>();

        if (query == null || query.trim().length() < 2) {
            return resultado;
        }

        String termo = query.trim();

        // Músicas
        musicaRepository.findByTituloContainingIgnoreCase(termo)
                .forEach(m -> resultado.add(m.getTitulo()));

        // Artistas
        artistaRepository.findByNomeContainingIgnoreCase(termo)
                .forEach(a -> resultado.add(a.getNome()));

        // Álbuns
        albumRepository.findByTituloContainingIgnoreCase(termo)
                .forEach(al -> resultado.add(al.getTitulo()));

        return resultado;
    }
}