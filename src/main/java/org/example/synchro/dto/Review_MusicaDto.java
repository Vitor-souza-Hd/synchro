package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Midia;
import org.example.synchro.entities.Review;
import org.example.synchro.entities.UserData;

@Data
@Getter
@Setter
@JsonPropertyOrder({
        "id",
        "autor",
        "rating",
        "review"
})
public class Review_MusicaDto {

    private Long id;

    private Integer rating;
    private String review;
    private String autor;

    Review_MusicaDto(Review review) {
        this.id = review.getId();
        this.rating = review.getRating();
        this.review = review.getReview();
        this.autor = review.getAuthor_Name();
    }
}
