package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity
@Getter
@Setter
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Midia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;
    private String titulo;
    private String descricao;

    @OneToMany(cascade = CascadeType.ALL,mappedBy = "midia")
    private Set<Review> review = new HashSet<>();
    public Midia() {

    }
    public Midia(String titulo, String descricao) {
        this.titulo = titulo;
        this.descricao = descricao;
    }

    public void addReview(Review review) {
        this.review.add(review);
        review.setMidia(this);
    }
    public void removeReview(Review review) {
        this.review.remove(review);
        review.setMidia(null);
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