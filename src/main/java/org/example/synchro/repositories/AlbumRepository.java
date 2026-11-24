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

    interface ArtistAlbumCandidate {
        String getArtistKey();
        Long getAlbumId();
        String getTitle();
        String getCover();
    }

    @Query(value = """
            SELECT ranked.artist_key AS "artistKey", ranked.album_id AS "albumId",
                   ranked.titulo AS "title", ranked.lastfm_image_url AS "cover"
            FROM (
                SELECT ar.nome_key AS artist_key, a.id AS album_id, m.titulo, m.lastfm_image_url,
                       row_number() OVER (PARTITION BY ar.nome_key
                           ORDER BY coalesce(m.lastfm_listeners, 0) DESC, coalesce(m.lastfm_playcount, 0) DESC,
                                    a.data_lancamento DESC NULLS LAST, a.id DESC) AS position_in_artist
                FROM artista ar
                JOIN album_artistas link ON link.artista_id = ar.id
                JOIN album a ON a.id = link.album_id
                JOIN midia m ON m.id = a.id
                WHERE ar.nome_key IN (:artistKeys)
                  AND NOT EXISTS (
                      SELECT 1 FROM review r JOIN user_data u ON u.id = r.autor_id
                      WHERE r.midia_id = a.id AND u.user_id = :userId
                  )
            ) ranked
            WHERE ranked.position_in_artist <= 3
            ORDER BY ranked.artist_key, ranked.position_in_artist
            """, nativeQuery = true)
    List<ArtistAlbumCandidate> findUnreviewedByArtists(@Param("userId") Long userId,
                                                       @Param("artistKeys") List<String> artistKeys);

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
