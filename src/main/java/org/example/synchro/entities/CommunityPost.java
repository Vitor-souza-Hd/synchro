package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_community_post",
        indexes = @Index(name = "ix_community_post_author", columnList = "author_id, id"))
public class CommunityPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant editedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached_artist_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Artista attachedArtist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached_media_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Midia attachedMedia;

    public CommunityPost(User author, String body) {
        this(author, body, null, null);
    }

    public CommunityPost(User author, String body, Artista attachedArtist, Midia attachedMedia) {
        this.author = author;
        this.body = body;
        this.createdAt = Instant.now();
        this.attachedArtist = attachedArtist;
        this.attachedMedia = attachedMedia;
    }

    public void edit(String body, Artista attachedArtist, Midia attachedMedia) {
        if (this.body.equals(body) && Objects.equals(this.attachedArtist, attachedArtist)
                && Objects.equals(this.attachedMedia, attachedMedia)) return;
        this.body = body;
        this.attachedArtist = attachedArtist;
        this.attachedMedia = attachedMedia;
        this.editedAt = Instant.now();
    }
}
