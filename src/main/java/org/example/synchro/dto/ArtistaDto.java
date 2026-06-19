package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@JsonPropertyOrder({
        "nome",
        "imgUrl",
        "biografia",
        "albuns",
        "singles"
})
@Getter
@Setter
public class ArtistaDto {
    private String nome;
    private String imgUrl;
    private String biografia;


    private Set<Musica_AlbumDto> singles = new HashSet<>();
    private Set<Album_ArtistaDto> albuns  = new HashSet<>();

    public ArtistaDto() {}
    public ArtistaDto(Artista artista) {
        this.nome = artista.getNome();
        this.imgUrl = artista.getImgUrl();
        this.biografia = artista.getBiografia();
        for (Musica musica : artista.getMusicas()) {
            if(musica.getAlbum() == null){
                this.singles.add(new Musica_AlbumDto(musica));
            }
        }
        for (Album album : artista.getAlbums()) {
            if(!albuns.contains(new Album_ArtistaDto(album))){
                this.albuns.add(new Album_ArtistaDto(album));
            }
        }
    }
}
