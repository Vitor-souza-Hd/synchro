package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@JsonPropertyOrder({
        "id",
        "titulo",
        "descricao",
        "musicas",
        "artistas"
})
public class AlbumDto {
    private Long id;
    private String titulo;
    private String descricao;
    private Set<Musica_AlbumDto>  musicas = new HashSet<Musica_AlbumDto>();
    private Set<Artista_MusicaDto> artistas = new HashSet<>();

    public AlbumDto() {

    }
    public AlbumDto(Album album) {
        this.id = album.getId();
        this.titulo = album.getTitulo();
        this.descricao = album.getDescricao();
        for (Musica musica : album.getMusicas()) {
            this.musicas.add(new Musica_AlbumDto(musica));
        }
        for (Artista artista : album.getArtistas()) {
            this.artistas.add(new Artista_MusicaDto(artista));
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Set<Musica_AlbumDto> getMusicas() {
        return musicas;
    }

    public void setMusicas(Set<Musica_AlbumDto> musicas) {
        this.musicas = musicas;
    }

    public Set<Artista_MusicaDto> getArtistas() {
        return artistas;
    }

    public void setArtistas(Set<Artista_MusicaDto> artistas) {
        this.artistas = artistas;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AlbumDto albumDto = (AlbumDto) o;
        return Objects.equals(id, albumDto.id) && Objects.equals(titulo, albumDto.titulo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, titulo);
    }
}
