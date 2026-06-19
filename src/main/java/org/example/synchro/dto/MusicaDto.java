package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Data
@JsonPropertyOrder({
        "id",
        "titulo",
        "descricao",
        "duracao",
        "genero",
        "artistas"
})
@Getter
@Setter
public class MusicaDto {
    private long id;
    private String titulo;
    private String descricao;
    private Duration duracao;
    private String genero;
    private Set<Artista_MusicaDto> artistas = new HashSet<>();

    public  MusicaDto() {

    }
    public MusicaDto(Musica musica)
    {
        this.id = musica.getId();
        this.titulo = musica.getTitulo();
        this.descricao = musica.getDescricao();
        this.duracao = musica.getDuracao();
        this.genero = musica.getGenero();

        for (Artista artista : musica.getArtistas()) {
            this.artistas.add(new Artista_MusicaDto(artista));
        }
    }

    public void AddArtista(Artista_MusicaDto artista) {
        this.artistas.add(artista);
    }

}
