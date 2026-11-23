package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_post_mention_notification",
        uniqueConstraints = @UniqueConstraint(columnNames = {"content_key", "recipient_id"}),
        indexes = @Index(name = "ix_post_mention_recipient", columnList = "recipient_id, created_at, id"))
public class PostMentionNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PostComment comment;

    @Column(name = "content_key", nullable = false, length = 40)
    private String contentKey;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    public PostMentionNotification(User recipient, CommunityPost post, PostComment comment, String contentKey) {
        this.recipient = recipient;
        this.post = post;
        this.comment = comment;
        this.contentKey = contentKey;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
