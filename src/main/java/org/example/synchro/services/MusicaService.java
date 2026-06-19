package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.MusicaDto;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MusicaService {

    private final MusicaRepository musicaRepository;
    private final ArtistaRepository artistaRepository;

    public MusicaDto findById(Long id) {
        Optional<Musica> m1 = musicaRepository.findById(id);
        return new MusicaDto(m1.get());
    }

    public MusicaDto findByTitulo(String titulo) {
        Musica m1 = musicaRepository.findByTitulo(titulo);
        return new MusicaDto(m1);
    }

    public List<MusicaDto> findByArtistas(String artista) {
        List<MusicaDto>dto = new ArrayList<>();
        List<Musica> m1 = musicaRepository.findByArtistas(artistaRepository.findByNome(artista));
        for (Musica musica : m1) {
            dto.add(new MusicaDto(musica));
        }
        return dto;
    }
}
