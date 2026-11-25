package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "tb_mural_signature")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MuralSignature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User author;

    @Column(nullable = false, length = 524288)
    private byte[] image;

    @Column(nullable = false)
    private Instant updatedAt;

    public MuralSignature(User author) {
        this.author = author;
    }

    public void update(byte[] image) {
        this.image = image;
        this.updatedAt = Instant.now();
    }
}
