package org.example.synchro.dto;

import lombok.Data;
import org.example.synchro.entities.Review;

@Data
public class ReviewDto {

    private Long id;
    private Integer rating;
    private String review;
    private String midiatype;
    private String author_name;
    private String midiaTitulo;
    private Long midiaId;
    private Long artistId;
    private String artistName;
    private String coverUrl;

    public ReviewDto(Review review) {
        this.id = review.getId();
        this.rating = review.getRating();
        this.review = review.getReview();
        this.midiatype = review.getMidiatype();
        this.author_name = review.getAuthor_Name();
        this.midiaTitulo = (review.getMidia() != null)
                ? review.getMidia().getTitulo()
                : review.getArtist() != null ? review.getArtist().getNome() : null;
        this.midiaId = (review.getMidia() != null)
                ? review.getMidia().getId()
                : null;
        this.artistId = review.getArtist() == null ? null : review.getArtist().getId();
    }
}
