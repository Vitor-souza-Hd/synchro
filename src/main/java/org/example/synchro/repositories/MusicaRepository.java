package org.example.synchro.repositories;

import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicaRepository extends JpaRepository<Musica, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select m from Musica m where m.id > :afterId
              and (m.genero is null or trim(m.genero) = '')
              and (m.generoConsultadoEm is null or m.generoConsultadoEm < :retryBefore)
              and exists (select s.id from LastFmScrobble s where s.musica = m and s.username = :username)
            order by m.id
            """)
    List<Musica> findGenreCandidates(long afterId, java.time.Instant retryBefore, String username,
                                    org.springframework.data.domain.Pageable pageable);
    java.util.Optional<Musica> findByTituloKey(String tituloKey);

    List<Musica> findByArtistas(Artista artista);

    Musica findByTitulo(String titulo);

    List<Musica> findByTituloContainingIgnoreCase(String titulo);

    List<Musica> findByTituloContainingIgnoreCaseOrArtistasNomeContainingIgnoreCase(
            String titulo,
            String nomeArtista
    );
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
