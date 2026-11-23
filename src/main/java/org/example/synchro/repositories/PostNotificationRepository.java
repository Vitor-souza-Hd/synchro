package org.example.synchro.repositories;

import org.example.synchro.entities.PostNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PostNotificationRepository extends JpaRepository<PostNotification, Long> {

    @EntityGraph(attributePaths = {"comment", "comment.author", "comment.author.data"})
    List<PostNotification> findByRecipientIdAndIdLessThanOrderByIdDesc(long recipientId, long before, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(long recipientId);

    @Modifying
    @Query("""
            UPDATE PostNotification n SET n.readAt = COALESCE(n.readAt, :now)
            WHERE n.id = :id AND n.recipient.id = :recipient
            """)
    int markRead(@Param("id") long id, @Param("recipient") long recipient, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE PostNotification n SET n.readAt = :now
            WHERE n.recipient.id = :recipient AND n.readAt IS NULL
            """)
    int markAllRead(@Param("recipient") long recipient, @Param("now") Instant now);
}
