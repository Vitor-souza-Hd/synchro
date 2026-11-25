package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @ElementCollection
    @CollectionTable(name = "tb_community_post_attachment", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "attachment_position")
    @BatchSize(size = 12)
    private List<PostCatalogAttachment> additionalAttachments = new ArrayList<>();

    public CommunityPost(User author, String body) {
        this(author, body, List.of());
    }

    public CommunityPost(User author, String body, List<PostCatalogAttachment> attachments) {
        this.author = author;
        this.body = body;
        this.createdAt = Instant.now();
        replaceAttachments(attachments);
    }

    public List<PostCatalogAttachment> catalogueAttachments() {
        var result = new ArrayList<PostCatalogAttachment>();
        // The original columns retain the first item, including posts created before multiple attachments.
        if (attachedArtist != null || attachedMedia != null) {
            result.add(new PostCatalogAttachment(attachedArtist, attachedMedia));
        }
        for (var item : additionalAttachments) {
            if (item != null && (item.getArtist() != null || item.getMedia() != null)) result.add(item);
        }
        return result;
    }

    public void edit(String body, List<PostCatalogAttachment> attachments) {
        if (this.body.equals(body) && catalogueAttachments().equals(attachments)) return;
        this.body = body;
        replaceAttachments(attachments);
        this.editedAt = Instant.now();
    }

    private void replaceAttachments(List<PostCatalogAttachment> attachments) {
        var first = attachments.isEmpty() ? null : attachments.get(0);
        attachedArtist = first == null ? null : first.getArtist();
        attachedMedia = first == null ? null : first.getMedia();
        additionalAttachments.clear();
        if (attachments.size() > 1) additionalAttachments.addAll(attachments.subList(1, attachments.size()));
    }
}
