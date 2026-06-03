package org.example.synchro.entities;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
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

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getNome() {
        return nome;
    }
    public void setNome(String nome) {
        this.nome = nome;
    }
    public Set<Musica> getMusicas() {
         return musicas;
    }
    public void addMusica(Musica musica) {
        this.musicas.add(musica);
    }
    public void removeMusica(Musica musica) {
        this.musicas.remove(musica);
    }
    public String getImgUrl() {
        return imgUrl;
    }
    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }
    public String getBiografia() {
        return Biografia;
    }
    public void setBiografia(String Biografia) {
        this.Biografia = Biografia;
    }
    public Set<Album> getAlbums() {
        return albums;
    }
    public void setAlbums(Set<Album> albums) {
        this.albums = albums;
    }
    public void addAlbum(Album album) {
        this.albums.add(album);
    }
    public void removeAlbum(Album album) {
        this.albums.remove(album);
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
