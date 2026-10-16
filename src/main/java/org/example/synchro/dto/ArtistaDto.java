package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;

import java.util.HashSet;
import java.util.Set;

@Data
@JsonPropertyOrder({
        "id",
        "nome",
        "imgUrl",
        "biografia",
        "albuns",
        "singles"
})
public class ArtistaDto {

    private Long id;
    private String nome;
    private String imgUrl;
    private String biografia;

    private Set<Musica_AlbumDto> singles = new HashSet<>();
    private Set<Album_ArtistaDto> albuns = new HashSet<>();

    public ArtistaDto() {}

    public ArtistaDto(Artista artista) {
        this.id = artista.getId();           // ← ADICIONADO
        this.nome = artista.getNome();
        this.imgUrl = artista.getImgUrl();
        this.biografia = artista.getBiografia();

        for (Musica musica : artista.getMusicas()) {
            if (musica.getAlbum() == null) {
                this.singles.add(new Musica_AlbumDto(musica));
            }
        }
        for (Album album : artista.getAlbums()) {
            Album_ArtistaDto dto = new Album_ArtistaDto(album);
            if (!albuns.contains(dto)) {
                this.albuns.add(dto);
            }
        }
    }
}