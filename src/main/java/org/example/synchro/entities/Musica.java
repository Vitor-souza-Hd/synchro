package org.example.synchro.entities;

import jakarta.persistence.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Musica extends Midia {
    private Duration duracao;
    private String genero;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "musica_artista",
            joinColumns = @JoinColumn(name = "musica_id"),
            inverseJoinColumns = @JoinColumn(name = "artista_id")
    )
    private Set<Artista> artistas = new HashSet<>();

    public Musica() {
        super();
    }

    public Musica(String titulo, String descricao, Duration duracao, String genero) {
        super(titulo,descricao);
        this.duracao = duracao;
        this.genero = genero;
    }

    public Duration getDuracao() {
        return duracao;
    }
    public void setDuracao(Duration duracao) {
        this.duracao = duracao;
    }
    public String getGenero() {
        return genero;
    }
    public void setGenero(String genero) {
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

    // Getters e Setters
    public Set<Artista> getArtistas() {
        return artistas;
    }

    public void setArtistas(Set<Artista> artistas) {
        this.artistas = artistas;
    }
}
}
