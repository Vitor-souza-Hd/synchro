
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
            JOIN FETCH r.midia media
            WHERE TYPE(media) IN (Album, Musica)
            ORDER BY r.id DESC
            """)
    List<Review> findRecentPublicReviews(Pageable pageable);

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
