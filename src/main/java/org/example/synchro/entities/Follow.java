package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_follow", uniqueConstraints = @UniqueConstraint(columnNames = {"seguidor_id", "seguido_id"}))
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seguidor_id", nullable = false)
    private User seguidor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seguido_id", nullable = false)
    private User seguido;

    @Column(nullable = false)
    private LocalDateTime dataSeguindo = LocalDateTime.now();

    public Follow(User seguidor, User seguido) {
        this.seguidor = seguidor;
        this.seguido = seguido;
    }
}
