package org.example.synchro.repositories;

import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlbumRepository extends JpaRepository<Album,Long> {
    public List<Album> findByArtistas(Artista artista);
    public Album findByTitulo(String titulo);
}
