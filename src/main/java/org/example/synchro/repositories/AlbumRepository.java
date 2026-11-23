package org.example.synchro.repositories;

import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Long> {
    java.util.Optional<Album> findByTituloKey(String tituloKey);

    List<Album> findByArtistas(Artista artista);

    @Query("""
            SELECT album FROM Album album
            WHERE EXISTS (SELECT artist.id FROM Album linked JOIN linked.artistas artist
                WHERE linked.id = album.id AND artist.nomeKey = :artistKey)
            AND NOT EXISTS (
                SELECT review.id FROM Review review
                WHERE review.midia.id = album.id AND review.author.user.id = :userId
            )
            ORDER BY coalesce(album.lastFm.listeners, 0) DESC, coalesce(album.lastFm.playcount, 0) DESC,
                album.dataLancamento DESC NULLS LAST, album.id DESC
            """)
    List<Album> findUnreviewedByArtist(@Param("userId") Long userId, @Param("artistKey") String artistKey,
                                     Pageable pageable);

    @Query("""
            SELECT album FROM Album album
            WHERE NOT EXISTS (SELECT review.id FROM Review review
                WHERE review.midia.id = album.id AND review.author.user.id = :userId)
            ORDER BY coalesce(album.lastFm.listeners, 0) DESC, coalesce(album.lastFm.playcount, 0) DESC,
                album.dataLancamento DESC NULLS LAST, album.id DESC
            """)
    List<Album> findUnreviewedPopular(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT album FROM Album album
            WHERE coalesce(album.lastFm.listeners, 0) > 0 OR coalesce(album.lastFm.playcount, 0) > 0
            ORDER BY coalesce(album.lastFm.listeners, 0) DESC, coalesce(album.lastFm.playcount, 0) DESC, album.id
            """)
    List<Album> findPopular(Pageable pageable);

    Album findByTitulo(String titulo);

    List<Album> findByTituloContainingIgnoreCase(String titulo);

    @Query("""
            select album from Album album
            where locate(lower(:term), lower(album.titulo)) > 0
            order by case
                when lower(album.titulo) = lower(:term) then 0
                when locate(lower(:term), lower(album.titulo)) = 1 then 1
                else 2 end, lower(album.titulo), album.id
            """)
    List<Album> searchCatalog(@Param("term") String term, Pageable pageable);
}
