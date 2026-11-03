
package org.example.synchro.entities;

import jakarta.persistence.*;

@Entity
@Table(
        name = "tb_favorite_track",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_favorite_track",
                columnNames = {"user_id", "track_key"}
        )
)
public class FavoriteTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "track_name", nullable = false, length = 255)
    private String trackName;

    @Column(name = "artist_name", nullable = false, length = 255)
    private String artistName;

    @Column(name = "track_key", nullable = false, length = 600)
    private String trackKey;

    public FavoriteTrack() {
    }

    public FavoriteTrack(
            User user,
            String trackName,
            String artistName,
            String trackKey
    ) {
        this.user = user;
        this.trackName = trackName;
        this.artistName = artistName;
        this.trackKey = trackKey;
    }

    public Long getId() {
        return id;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getArtistName() {
        return artistName;
    }
}
