package org.example.synchro.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCatalogAttachment {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Artista artist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Midia media;

    public PostCatalogAttachment(Artista artist, Midia media) {
        this.artist = artist;
        this.media = media;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PostCatalogAttachment attachment)) return false;
        return Objects.equals(artistId(), attachment.artistId()) && Objects.equals(mediaId(), attachment.mediaId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(artistId(), mediaId());
    }

    private Long artistId() {
        return artist == null ? null : artist.getId();
    }

    private Long mediaId() {
        return media == null ? null : media.getId();
    }
}
