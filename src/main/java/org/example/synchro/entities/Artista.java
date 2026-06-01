package org.example.synchro.entities;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Artista implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;

    @ManyToMany(mappedBy = "artistas", fetch = FetchType.LAZY)
    private Set<Musica> musicas = new HashSet<>();

    public Artista() {
    }

    public Artista(String nome) {
        this.nome = nome;
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
    public Set<Musica> getMusicas() {
         return musicas;
    }
    public void addMusica(Musica musica) {
        this.musicas.add(musica);
    }
    public void removeMusica(Musica musica) {
        this.musicas.remove(musica);
    }
}
