package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(indexes = @Index(name = "idx_scrobble_user_time", columnList = "username,playedAt"))
@Getter
@Setter
public class LastFmScrobble {
    @Id
    @Column(length = 64)
    private String id;
    private String username;
    private long playedAt;
    @ManyToOne(optional = false)
    private Musica musica;
    @ManyToOne(optional = false)
    private Artista artista;
    @ManyToOne
    private Album album;
    @Lob
    private String rawJson;
}
