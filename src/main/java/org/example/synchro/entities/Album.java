package org.example.synchro.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
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

    public void addMusica(Musica musica){
        this.musicas.add(musica);
        musica.setAlbum(this);
    }

    public void addArtista(Artista artista){
        artistas.add(artista);
        artista.getAlbums().add(this);
    }

    public void removeMusica(Musica musica){
        this.musicas.remove(musica);
        musica.setAlbum(null);
    }
    public void removeArtista(Artista artista){
        artistas.remove(artista);
        artista.getAlbums().remove(this);
    }
}
