package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "tb_follow")
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "seguidor_id")
    private User seguidor;

    @ManyToOne
    @JoinColumn(name = "seguido_id")
    private User seguido;

    private LocalDateTime dataSeguindo = LocalDateTime.now();

    public Follow() {}

    public Follow(User seguidor, User seguido) {
        this.seguidor = seguidor;
        this.seguido = seguido;
    }
}