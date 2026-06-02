package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@JsonPropertyOrder({
        "id",
        "titulo",
        "descricao",
        "duracao",
        "genero",
        "artistas"
})

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


    public void setArtistas(Set<Artista_MusicaDto> artistas) {
        this.artistas = artistas;
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
    public Duration getDuracao() {
        return duracao;
    }
    public void setDuracao(Duration duracao) {
        this.duracao = duracao;
    }
    public String getGenero() {
        return genero;
    }
    public void setGenero(String genero) {
        this.genero = genero;
    }
    public Set<Artista_MusicaDto> getArtistas() {
        return artistas;
    }
    public void AddArtista(Artista_MusicaDto artista) {
        this.artistas.add(artista);
    }

}
