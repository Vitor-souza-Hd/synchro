package org.example.synchro.repositories;

import jakarta.persistence.LockModeType;
import org.example.synchro.entities.CommunityPost;
import org.example.synchro.entities.UserRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    @EntityGraph(attributePaths = {"attachedArtist", "attachedMedia"})
    List<CommunityPost> findByIdLessThanOrderByIdDesc(long before, Pageable pageable);

    @EntityGraph(attributePaths = {"attachedArtist", "attachedMedia"})
    List<CommunityPost> findByAuthorIdAndIdLessThanOrderByIdDesc(long authorId, long before, Pageable pageable);

    @EntityGraph(attributePaths = {"attachedArtist", "attachedMedia"})
    @Query("""
            SELECT post FROM CommunityPost post WHERE post.id < :before AND EXISTS (
                SELECT 1 FROM Follow relation WHERE relation.seguidor.id = :viewer AND relation.seguido.id = post.author.id
            ) ORDER BY post.id DESC
            """)
    List<CommunityPost> findFollowing(@Param("viewer") long viewer, @Param("before") long before, Pageable pageable);

    @EntityGraph(attributePaths = {"attachedArtist", "attachedMedia"})
    @Query("""
            SELECT post FROM CommunityPost post WHERE post.id < :before AND EXISTS (
                SELECT 1 FROM PostInteraction reaction WHERE reaction.user.id = :viewer
                    AND reaction.post.id = post.id AND reaction.saved = true
            ) ORDER BY post.id DESC
            """)
    List<CommunityPost> findSaved(@Param("viewer") long viewer, @Param("before") long before, Pageable pageable);

    interface AuthorProfile {
        Long getId();
        String getUsername();
        UserRole getRole();
        String getProfileImageUrl();
        String getLastFmImageUrl();
    }

    @Query("""
            SELECT author.id AS id, author.username AS username, author.role AS role,
                   data.profileImageUrl AS profileImageUrl, data.lastFmImageUrl AS lastFmImageUrl
            FROM User author LEFT JOIN author.data data WHERE author.id IN :ids
            """)
    List<AuthorProfile> findAuthorProfiles(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT post FROM CommunityPost post WHERE post.id = :id")
    Optional<CommunityPost> findByIdForUpdate(@Param("id") long id);
}
