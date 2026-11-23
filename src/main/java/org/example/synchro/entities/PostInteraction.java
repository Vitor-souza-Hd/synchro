package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_post_interaction",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}),
        indexes = {
                @Index(name = "ix_post_interaction_likes", columnList = "post_id, liked"),
                @Index(name = "ix_post_interaction_saved", columnList = "user_id, saved, post_id")
        })
public class PostInteraction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false)
    private boolean liked;

    @Column(nullable = false)
    private boolean saved;

    public PostInteraction(CommunityPost post, User user) {
        this.post = post;
        this.user = user;
    }

    public void setLiked(boolean liked) {
        this.liked = liked;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }
}
