package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
public class Artista implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private String imgUrl;
    private String Biografia;

    @ManyToMany(mappedBy = "artistas", fetch = FetchType.LAZY)
    private Set<Musica> musicas = new HashSet<>();

    @ManyToMany(mappedBy = "artistas", fetch = FetchType.LAZY)
    private Set<Album> albums = new HashSet<>();

    public Artista() {
    }

    public Artista(String nome, String Biografia, String imgUrl) {
        this.nome = nome;
        this.imgUrl = imgUrl;
        this.Biografia = Biografia;
    }

    public void addAlbum(Album album) {
        albums.add(album);
        album.addArtista(this);
    }
    public void removeAlbum(Album album) {
        this.albums.remove(album);
        album.getArtistas().remove(this);
    }
    public void addMusica(Musica musica) {
        musicas.add(musica);
        musica.addArtista(this);
    }
    public void removeMusica(Musica musica) {
        musicas.remove(musica);
        musica.getArtistas().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Artista artista = (Artista) o;
        return Objects.equals(id, artista.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
