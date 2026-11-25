package org.example.synchro.repositories;

import org.example.synchro.entities.MuralSignature;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MuralSignatureRepository extends JpaRepository<MuralSignature, Long> {
    interface Entry {
        Long getId();
        String getUsername();
        Instant getUpdatedAt();
    }

    @Query("""
            SELECT signature.id AS id, author.username AS username, signature.updatedAt AS updatedAt
            FROM MuralSignature signature JOIN signature.author author
            WHERE signature.id < :before ORDER BY signature.id DESC
            """)
    List<Entry> findPage(@Param("before") long before, Pageable page);

    Optional<MuralSignature> findByAuthorId(Long authorId);
}
