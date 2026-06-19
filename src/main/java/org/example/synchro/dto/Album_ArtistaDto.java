package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Musica;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@JsonPropertyOrder({
        "id",
        "titulo",
        "descricao",
        "musicas"
})
@Getter
@Setter
public class Album_ArtistaDto {
    private Long id;
    private String titulo;
    private String descricao;
    private Set<Musica_AlbumDto> musicas = new HashSet<>();

    public Album_ArtistaDto() {}

    public Album_ArtistaDto(Album album) {
        this.id = album.getId();
        this.titulo = album.getTitulo();
        this.descricao = album.getDescricao();
        for (Musica musica : album.getMusicas()) {
            this.musicas.add(new Musica_AlbumDto(musica));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Album_ArtistaDto that = (Album_ArtistaDto) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
