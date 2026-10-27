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


    // =====================================================
    // IDENTIFICAR MUSICA POR TITULO E ARTISTA
    // =====================================================

    @org.springframework.data.jpa.repository.Query("""
        SELECT DISTINCT m
        FROM Musica m
        JOIN m.artistas a
        WHERE LOWER(m.titulo) = LOWER(:titulo)
          AND LOWER(a.nome) = LOWER(:artista)
    """)
    List<Musica> buscarPorTituloEArtista(
            @org.springframework.data.repository.query.Param("titulo")
            String titulo,

            @org.springframework.data.repository.query.Param("artista")
            String artista
    );

}

