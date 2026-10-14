package org.example.synchro.repositories;

import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicaRepository extends JpaRepository<Musica, Long> {

    List<Musica> findByArtistas(Artista artista);

    Musica findByTitulo(String titulo);

    List<Musica> findByTituloContainingIgnoreCase(String titulo);

    List<Musica> findByTituloContainingIgnoreCaseOrArtistasNomeContainingIgnoreCase(
            String titulo,
            String nomeArtista
    );
}

