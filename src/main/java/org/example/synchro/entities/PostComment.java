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
@Table(name = "tb_post_comment",
        indexes = @Index(name = "ix_post_comment_post", columnList = "post_id, id"))
public class PostComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PostComment parent;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant editedAt;

    private Instant deletedAt;

    public PostComment(CommunityPost post, User author, PostComment parent, String body) {
        this.post = post;
        this.author = author;
        this.parent = parent;
        this.body = body;
        this.createdAt = now();
    }

    public void edit(String body) {
        if (this.body.equals(body)) return;
        this.body = body;
        editedAt = now();
    }

    public void delete() {
        if (deletedAt == null) {
            body = "";
            deletedAt = now();
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
