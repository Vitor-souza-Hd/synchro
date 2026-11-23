package org.example.synchro.repositories;

import org.example.synchro.entities.PostInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostInteractionRepository extends JpaRepository<PostInteraction, Long> {
    interface LikeCount {
        Long getPostId();
        long getTotal();
    }

    @Query("""
            SELECT interaction.post.id AS postId, COUNT(interaction) AS total
            FROM PostInteraction interaction
            WHERE interaction.liked = true AND interaction.post.id IN :postIds
            GROUP BY interaction.post.id
            """)
    List<LikeCount> countLikes(@Param("postIds") Collection<Long> postIds);

    List<PostInteraction> findByUserIdAndPostIdIn(long userId, Collection<Long> postIds);

    Optional<PostInteraction> findByPostIdAndUserId(long postId, long userId);
}
