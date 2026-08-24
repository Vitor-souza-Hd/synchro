package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.entities.Review;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Data
@JsonPropertyOrder({
        "id",
        "titulo",
        "descricao",
        "musicas",
        "artistas"
})
@Getter
@Setter
public class AlbumDto {
    private Long id;
    private String titulo;
    private String descricao;
    private Set<Musica_AlbumDto>  musicas = new HashSet<Musica_AlbumDto>();
    private Set<Artista_MusicaDto> artistas = new HashSet<>();
    private Set<Review_AlbumDto> reviews = new HashSet<>();

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
        for (Review review : album.getReview()) {
            this.reviews.add(new Review_AlbumDto(review));
        }
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
