package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_lastfm_profile_ranking", uniqueConstraints = @UniqueConstraint(
        name = "uk_lastfm_profile_ranking", columnNames = {"user_id", "ranking_type", "ranking_period"}))
public class LastFmProfileRanking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "ranking_type", nullable = false, length = 8)
    private String type;

    @Column(name = "ranking_period", nullable = false, length = 8)
    private String period;

    @Column(nullable = false)
    private String lastFmUsername;

    @Column(columnDefinition = "text", nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant updatedAt;

    public LastFmProfileRanking(User user, String type, String period) {
        this.user = user;
        this.type = type;
        this.period = period;
    }

    public void update(String lastFmUsername, String payload, Instant updatedAt) {
        this.lastFmUsername = lastFmUsername;
        this.payload = payload;
        this.updatedAt = updatedAt;
    }
}
