package org.example.synchro.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Embeddable
@Getter
@Setter
public class LastFmMetadata {
    @Column(name = "lastfm_mbid")
    private String mbid;
    @Column(name = "lastfm_artist_name", length = 1024)
    private String artistName;
    @Column(name = "lastfm_url", length = 2048)
    private String url;
    @Column(name = "lastfm_image_url", length = 2048)
    private String imageUrl;
    @Column(name = "lastfm_listeners")
    private Long listeners;
    @Column(name = "lastfm_playcount")
    private Long playcount;
    @Column(name = "lastfm_tags_json", columnDefinition = "text")
    private String tagsJson;
    @Column(name = "lastfm_raw_json", columnDefinition = "text")
    private String rawJson;
    @Column(name = "lastfm_updated_at")
    private Instant updatedAt;
}
