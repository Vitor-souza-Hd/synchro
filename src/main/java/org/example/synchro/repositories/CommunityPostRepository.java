package org.example.synchro.repositories;

import jakarta.persistence.LockModeType;
import org.example.synchro.entities.CommunityPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    @EntityGraph(attributePaths = {"author", "author.data", "attachedArtist", "attachedMedia"})
    List<CommunityPost> findByIdLessThanOrderByIdDesc(long before, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.data", "attachedArtist", "attachedMedia"})
    List<CommunityPost> findByAuthorIdAndIdLessThanOrderByIdDesc(long authorId, long before, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT post FROM CommunityPost post WHERE post.id = :id")
    Optional<CommunityPost> findByIdForUpdate(@Param("id") long id);
}
