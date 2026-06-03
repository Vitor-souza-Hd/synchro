package org.example.synchro.entities;

import jakarta.persistence.*;

import java.util.Objects;

@MappedSuperclass
public abstract class Midia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;
    private  String titulo;
    private  String descricao;

    public Midia() {

    }
    public Midia(String titulo, String descricao) {
        this.titulo = titulo;
        this.descricao = descricao;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Midia midia = (Midia) o;
        return Objects.equals(id, midia.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}