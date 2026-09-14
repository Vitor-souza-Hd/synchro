package org.example.synchro.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Review;

@Data
@Getter
@Setter
public class ReviewDto {
    private Long id;
    private Integer rating;
    private String review;
    private String midiatype;
    private String author_name;

    public ReviewDto(Review review) {
        this.id = review.getId();
        this.rating = review.getRating();
        this.review = review.getReview();
        this.midiatype = review.getMidiatype();
        this.author_name = review.getAuthor_Name();
    }
}
