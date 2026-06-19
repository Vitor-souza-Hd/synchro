package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.MusicaDto;
import org.example.synchro.entities.Musica;
import org.example.synchro.repositories.MusicaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MusicaService {

    @Autowired
    private final MusicaRepository musicaRepository;

    public MusicaDto findById(Long id) {
        Optional<Musica> m1 = musicaRepository.findById(id);
        MusicaDto musicaDto = new MusicaDto(m1.get());


        return musicaDto;

    }
}
