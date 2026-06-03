package org.example.synchro.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Album extends Midia{

    private LocalDate dataLancamento;

    @OneToMany(
            mappedBy = "album",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private Set<Musica> musicas = new HashSet<Musica>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable( name = "album_artistas",
            joinColumns = @JoinColumn(name = "album_id"),
            inverseJoinColumns = @JoinColumn(name = "artista_id")
    )
    private Set<Artista> artistas = new HashSet<>();

    public Album(){

    };

    public Album(String titulo, String descricao, LocalDate dataLancamento) {
        super(titulo, descricao);
        this.dataLancamento = dataLancamento;
    }

    public LocalDate getDataLancamento() {
        return dataLancamento;
    }

    public void setDataLancamento(LocalDate dataLancamento) {
        this.dataLancamento = dataLancamento;
    }

    public Set<Musica> getMusicas() {
        return musicas;
    }

    public Set<Artista> getArtistas() {
        return artistas;
    }

    public void addMusica(Musica musica){
        this.musicas.add(musica);
        musica.setAlbum(this);
    }

    public void addArtista(Artista artista){
        artistas.add(artista);
        artista.addAlbum(this);
    }
}
