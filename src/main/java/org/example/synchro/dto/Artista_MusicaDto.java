package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Artista;

import java.util.Objects;
@Data
@JsonPropertyOrder({
        "id",
        "nome",
        "biografia",
        "imgUrl"
})
@Getter
@Setter
public class Artista_MusicaDto {
    private long id;
    private String nome;
    private String imgUrl;
    private String biografia;

    public  Artista_MusicaDto(Artista artista) {
        this.id = artista.getId();
        this.nome = artista.getNome();
        this.imgUrl =artista.getImgUrl();
        this.biografia = artista.getBiografia();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Artista_MusicaDto that = (Artista_MusicaDto) o;
        return id == that.id && Objects.equals(nome, that.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome);
    }

}
