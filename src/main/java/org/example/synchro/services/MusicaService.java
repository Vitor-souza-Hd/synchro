package org.example.synchro.services;

import org.example.synchro.dto.MusicaDto;
import org.example.synchro.entities.Musica;
import org.example.synchro.repositories.MusicaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MusicaService {

    @Autowired
    private MusicaRepository musicaRepository;

    public MusicaDto findById(Long id) {
        Optional<Musica> m1 = musicaRepository.findById(id);
        MusicaDto musicaDto = new MusicaDto(m1.get());


        return musicaDto;

    }
}
