
package org.example.synchro.entities;

import jakarta.persistence.*;

@Entity
@Table(
        name = "tb_favorite_artist",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_favorite_artist",
                        columnNames = {"user_id", "artist_key"}
                )
        }
)
public class FavoriteArtist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "artist_name", nullable = false, length = 255)
    private String artistName;

    @Column(name = "artist_key", nullable = false, length = 255)
    private String artistKey;

    public FavoriteArtist() {
    }

    public FavoriteArtist(
            User user,
            String artistName,
            String artistKey
    ) {
        this.user = user;
        this.artistName = artistName;
        this.artistKey = artistKey;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getArtistKey() {
        return artistKey;
    }
}
