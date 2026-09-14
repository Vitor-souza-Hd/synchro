package org.example.synchro.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class ReviewRequest {

    @Min(value=0, message = "valor deve estar no intevalo de 0 a 10")
    @Max(value=10, message = "valor deve estar no intevalo de 0 a 10")
    private Integer rating;
    private String review;

    public ReviewRequest(Integer rating, String review) {
        this.rating = rating;
        this.review = review;
    }
}
