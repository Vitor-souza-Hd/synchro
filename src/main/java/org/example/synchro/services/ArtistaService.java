package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.ArtistaDto;
import org.example.synchro.entities.Artista;
import org.example.synchro.repositories.ArtistaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtistaService {

    private final ArtistaRepository artistaRepository;

    public Artista findByNome(String nome) {
        return artistaRepository.findByNome(nome);
    }

    public ArtistaDto findByNomeDto(String nome) {
        return new ArtistaDto(findByNome(nome));
    }

    public List<ArtistaDto> findAll() {
        return artistaRepository.findAll()
                .stream()
                .map(ArtistaDto::new)
                .toList();
    }
}