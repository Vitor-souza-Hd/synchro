
package org.example.synchro.repositories;

import org.example.synchro.entities.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository
        extends JpaRepository<Review, Long> {

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.author author
            JOIN FETCH author.user
            LEFT JOIN FETCH r.midia media
            LEFT JOIN FETCH r.artist artist
            WHERE artist IS NOT NULL OR TYPE(media) IN (Album, Musica)
            ORDER BY r.id DESC
            """)
    List<Review> findRecentPublicReviews(Pageable pageable);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.author author
            JOIN FETCH author.user
            LEFT JOIN FETCH r.midia media
            LEFT JOIN FETCH r.artist artist
            WHERE author.user.id = :userId
              AND (artist IS NOT NULL OR TYPE(media) IN (Album, Musica))
              AND r.id < :before
            ORDER BY r.id DESC
            """)
    List<Review> findPublicReviewsByUser(@Param("userId") long userId, @Param("before") long before, Pageable pageable);

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.author author
            JOIN FETCH author.user
            JOIN FETCH r.artist artist
            WHERE artist.id = :artistId AND r.id < :before
            ORDER BY r.id DESC
            """)
    List<Review> findPublicReviewsByArtist(@Param("artistId") long artistId, @Param("before") long before, Pageable pageable);

    interface ProfileItem {
        Long getId();
        String getTitle();
    }

    @Query("""
            select r.id as id, coalesce(artist.nome, media.titulo) as title
            from Review r
            left join r.midia media
            left join r.artist artist
            where r.author.user.id = :userId and (artist is not null or type(media) in (Album, Musica))
            order by r.id desc
            """)
    List<ProfileItem> findProfileItems(@Param("userId") long userId);

    @Query("""
            select r from Review r
            join fetch r.author author
            join fetch author.user
            left join fetch r.midia media
            left join fetch r.artist artist
            where author.user.id = :userId and r.id in :ids
              and (artist is not null or type(media) in (Album, Musica))
            order by r.id desc
            """)
    List<Review> findProfileReviews(@Param("userId") long userId, @Param("ids") List<Long> ids);

    Optional<Review> findByAuthorUserIdAndArtistId(Long userId, Long artistId);

    long countByArtistId(long artistId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.artist.id = :artistId")
    Double averageForArtist(@Param("artistId") long artistId);

    Optional<Review> findByAuthorIdAndMidiaId(
            Long authorId,
            Long midiaId
    );

    @Query("""
            SELECT r FROM Review r
            LEFT JOIN FETCH r.midia
            LEFT JOIN FETCH r.artist
            WHERE r.author.user.id = :userId
            ORDER BY r.id DESC
            """)
    List<Review> findReviewsBySynchroUserId(
            @Param("userId") Long userId
    );

    @Query("""
            SELECT AVG(r.rating) FROM Review r
            WHERE r.midia.id = :midiaId
            """)
    Double calcularMediaPorMidia(
            @Param("midiaId") Long midiaId
    );

    @Query("""
            SELECT COUNT(r) FROM Review r
            WHERE r.midia.id = :midiaId
            """)
    Long contarReviewsPorMidia(
            @Param("midiaId") Long midiaId
    );
}
