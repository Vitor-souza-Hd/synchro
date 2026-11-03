
package org.example.synchro.entities;

import jakarta.persistence.*;

@Entity
@Table(
        name = "tb_favorite_album",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_favorite_album",
                columnNames = {"user_id", "release_group_id"}
        )
)
public class FavoriteAlbum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(
            name = "release_group_id",
            nullable = false,
            length = 36
    )
    private String releaseGroupId;

    @Column(name = "album_title", nullable = false, length = 255)
    private String albumTitle;

    @Column(name = "artist_name", nullable = false, length = 255)
    private String artistName;

    public FavoriteAlbum() {
    }

    public FavoriteAlbum(
            User user,
            String releaseGroupId,
            String albumTitle,
            String artistName
    ) {
        this.user = user;
        this.releaseGroupId = releaseGroupId;
        this.albumTitle = albumTitle;
        this.artistName = artistName;
    }

    public Long getId() {
        return id;
    }

    public String getReleaseGroupId() {
        return releaseGroupId;
    }

    public String getAlbumTitle() {
        return albumTitle;
    }

    public String getArtistName() {
        return artistName;
    }
}
