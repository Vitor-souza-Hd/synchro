package org.example.synchro.repositories;

import org.example.synchro.entities.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByAuthorIdAndMidiaId(Long authorId, Long midiaId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.midia.id = :midiaId")
    Double calcularMediaPorMidia(@Param("midiaId") Long midiaId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.midia.id = :midiaId")
    Long contarReviewsPorMidia(@Param("midiaId") Long midiaId);
}