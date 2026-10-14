package org.example.synchro.repositories;

import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Long> {

    List<Album> findByArtistas(Artista artista);

    Album findByTitulo(String titulo);

    List<Album> findByTituloContainingIgnoreCase(String titulo);
}
