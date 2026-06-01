package org.example.synchro.services;

import org.example.synchro.entities.Musica;
import org.example.synchro.repositories.MusicaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MusicaService {

    @Autowired
    private MusicaRepository musicaRepository;

    public Optional<Musica> findById(Long id) {
        return musicaRepository.findById(id);
    }
}
