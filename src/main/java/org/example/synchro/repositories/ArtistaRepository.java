package org.example.synchro.repositories;

import org.example.synchro.entities.Artista;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtistaRepository extends JpaRepository<Artista, Long> {
    java.util.Optional<Artista> findByNomeKey(String nomeKey);

    Artista findByNome(String nome);

    List<Artista> findByNomeContainingIgnoreCase(String nome);

    @Query("""
            select artist from Artista artist
            where locate(lower(:term), lower(artist.nome)) > 0
            order by case
                when lower(artist.nome) = lower(:term) then 0
                when locate(lower(:term), lower(artist.nome)) = 1 then 1
                else 2 end, lower(artist.nome), artist.id
            """)
    List<Artista> searchCatalog(@Param("term") String term, Pageable pageable);
    List<Artista> findByNomeIgnoreCase(String nome);
}
