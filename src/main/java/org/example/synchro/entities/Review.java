package org.example.synchro.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"autor_id", "artista_id"}),
        indexes = @Index(name = "ix_review_artist", columnList = "artista_id, id"))
@Getter
@Setter
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Min(value=0, message = "valor deve estar no intevalo de 0 a 10")
    @Max(value=10, message = "valor deve estar no intevalo de 0 a 10")
    private Integer rating;
    private String review;

    @ManyToOne
    @JoinColumn(name = "autor_id")
    private UserData author;
    private String author_Name;

    @ManyToOne
    @JoinColumn(name = "midia_id")
    private Midia midia;
    private String midiatype;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artista_id")
    private Artista artist;

    public Review() {

    }
    public Review(Integer rating, String review, Midia midia,UserData author) {
        midia.addReview(this);
        author.addReview(this);
        this.rating = rating;
        this.review = review;
        this.midiatype = midia.getClass().getSimpleName();
        this.author_Name = author.getUsername();
    }

    public Review(Integer rating, String review, Artista artist, UserData author) {
        this.rating = rating;
        this.review = review;
        this.artist = artist;
        this.midiatype = "Artista";
        this.author_Name = author.getUsername();
        author.addReview(this);
    }

}
