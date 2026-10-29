package org.example.synchro.repositories;

import org.example.synchro.entities.Artista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtistaRepository extends JpaRepository<Artista, Long> {
    java.util.Optional<Artista> findByNomeKey(String nomeKey);

    Artista findByNome(String nome);

    List<Artista> findByNomeContainingIgnoreCase(String nome);
}

