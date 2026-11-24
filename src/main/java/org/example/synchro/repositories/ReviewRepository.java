
package org.example.synchro.repositories;

import org.example.synchro.entities.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
            WHERE r.author.user.id = :userId
            ORDER BY r.id DESC
            """)
    List<Review> findReviewsBySynchroUserId(
            @Param("userId") Long userId
    );

    @EntityGraph(attributePaths = {"midia", "artist", "author.user"})
    List<Review> findByIdLessThanOrderByIdDesc(long before, Pageable pageable);

    @EntityGraph(attributePaths = {"midia", "artist", "author.user"})
    List<Review> findByMidiaIdAndIdLessThanOrderByIdDesc(long midiaId, long before, Pageable pageable);

    Optional<Review> findByAuthorUserIdAndMidiaId(long userId, long midiaId);

    interface RatingSummary {
        Double getAverage();
        long getTotal();
    }

    @Query("SELECT AVG(r.rating) AS average, COUNT(r) AS total FROM Review r WHERE r.midia.id = :midiaId")
    RatingSummary summarizeRating(@Param("midiaId") Long midiaId);
}
