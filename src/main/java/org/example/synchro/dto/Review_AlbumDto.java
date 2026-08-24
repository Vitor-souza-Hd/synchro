package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Review;

@Data
@Getter
@Setter
@JsonPropertyOrder({
        "id",
        "autor",
        "rating",
        "review"
})
public class Review_AlbumDto {

    private Long id;

    private Integer rating;
    private String review;
    private String autor;

    Review_AlbumDto(Review review) {
        this.id = review.getId();
        this.rating = review.getRating();
        this.review = review.getReview();
        this.autor = review.getAuthor_Name();
    }
}