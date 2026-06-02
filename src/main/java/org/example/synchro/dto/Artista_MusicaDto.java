package org.example.synchro.dto;

import org.example.synchro.entities.Artista;

public class Artista_MusicaDto {
    private long id;
    private String nome;

    public  Artista_MusicaDto(Artista artista) {
        this.id = artista.getId();
        this.nome = artista.getNome();
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
}
