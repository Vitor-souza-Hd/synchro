package org.example.synchro.repositories;

import org.example.synchro.entities.PostMentionNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PostMentionNotificationRepository extends JpaRepository<PostMentionNotification, Long> {

    @Query("SELECT n.recipient.id FROM PostMentionNotification n WHERE n.contentKey = :content")
    List<Long> notifiedRecipients(@Param("content") String content);

    @EntityGraph(attributePaths = {"post", "post.author", "post.author.data", "comment", "comment.author", "comment.author.data"})
    @Query("""
            SELECT n FROM PostMentionNotification n
            WHERE n.recipient.id = :recipient AND
                (n.createdAt < :time OR (n.createdAt = :time AND n.id < :before))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<PostMentionNotification> inbox(@Param("recipient") long recipient, @Param("time") Instant time,
                                        @Param("before") long before, Pageable page);

    long countByRecipientIdAndReadAtIsNull(long recipientId);

    @Modifying
    @Query("""
            UPDATE PostMentionNotification n SET n.readAt = COALESCE(n.readAt, :now)
            WHERE n.id = :id AND n.recipient.id = :recipient
            """)
    int markRead(@Param("id") long id, @Param("recipient") long recipient, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE PostMentionNotification n SET n.readAt = :now
            WHERE n.recipient.id = :recipient AND n.readAt IS NULL
            """)
    int markAllRead(@Param("recipient") long recipient, @Param("now") Instant now);
}
