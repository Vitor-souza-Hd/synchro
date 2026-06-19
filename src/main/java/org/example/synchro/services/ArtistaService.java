package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Artista;
import org.example.synchro.repositories.ArtistaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArtistaService {

    private final ArtistaRepository artistaRepository;

    public Artista findByNome (String nome) {
        return artistaRepository.findByNome(nome);
    }
}
