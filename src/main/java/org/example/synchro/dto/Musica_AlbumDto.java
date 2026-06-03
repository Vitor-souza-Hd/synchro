package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.example.synchro.entities.Musica;

import java.time.Duration;
import java.util.Objects;
@JsonPropertyOrder({
        "id",
        "titulo",
        "descricao",
        "duracao"
})
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
