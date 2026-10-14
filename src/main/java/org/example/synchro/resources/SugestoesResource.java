
package org.example.synchro.resources;

import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SugestoesResource {

    @Autowired
    private MusicaRepository musicaRepository;

    @Autowired
    private ArtistaRepository artistaRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @GetMapping("/sugestoes")
    public List<String> sugestoes(
            @RequestParam(name = "q", defaultValue = "") String query
    ) {
        List<String> resultado = new ArrayList<>();

        String termo = query.trim();

        if (termo.length() < 2) {
            return resultado;
        }

        musicaRepository.findByTituloContainingIgnoreCase(termo)
                .forEach(m -> resultado.add(m.getTitulo()));

        artistaRepository.findByNomeContainingIgnoreCase(termo)
                .forEach(a -> resultado.add(a.getNome()));

        albumRepository.findByTituloContainingIgnoreCase(termo)
                .forEach(al -> resultado.add(al.getTitulo()));

        return resultado;
    }
}
