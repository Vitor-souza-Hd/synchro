package org.example.synchro.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Entity
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

}
