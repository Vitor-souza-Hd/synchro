package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Musica;

import java.time.Duration;
import java.util.Objects;

@Data
@JsonPropertyOrder({
        "id",
        "titulo",
        "descricao",
        "duracao"
})
@Getter
@Setter
public class Musica_AlbumDto {
    private Long id;
    private String titulo;
    private String descricao;
    private Duration duracao;

    public Musica_AlbumDto(Musica musica) {
        this.id = musica.getId();
        this.titulo = musica.getTitulo();
        this.descricao = musica.getDescricao();
        this.duracao = musica.getDuracao();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Musica_AlbumDto that = (Musica_AlbumDto) o;
        return Objects.equals(id, that.id) && Objects.equals(titulo, that.titulo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, titulo);
    }
}
