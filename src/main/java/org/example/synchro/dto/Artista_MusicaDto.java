package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.example.synchro.entities.Artista;
@JsonPropertyOrder({
        "id",
        "nome",
        "biografia",
        "imgUrl"
})
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

    public void setId(long id) {
        this.id = id;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public String getBiografia() {
        return biografia;
    }

    public void setBiografia(String biografia) {
        this.biografia = biografia;
    }
}
