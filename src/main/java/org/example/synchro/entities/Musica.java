package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class Musica extends Midia implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Duration duracao;
    private String genero;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "musica_artista",
            joinColumns = @JoinColumn(name = "musica_id"),
            inverseJoinColumns = @JoinColumn(name = "artista_id")
    )
    private Set<Artista> artistas = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    public Musica() {
        super();
    }

    public Musica(String titulo, String descricao, Duration duracao, String genero) {
        super(titulo,descricao);
        this.duracao = duracao;
        this.genero = genero;
    }


    public void addArtista(Artista artista) {
        this.artistas.add(artista);
        artista.getMusicas().add(this);
    }

    public void removeArtista(Artista artista) {
        this.artistas.remove(artista);
        artista.getMusicas().remove(this);
    }

}

