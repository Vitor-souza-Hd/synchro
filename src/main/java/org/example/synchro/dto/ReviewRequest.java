package org.example.synchro.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class ReviewRequest {

    @NotNull
    @Min(value=0, message = "valor deve estar no intevalo de 0 a 10")
    @Max(value=10, message = "valor deve estar no intevalo de 0 a 10")
    @jakarta.validation.constraints.NotNull
    private Integer rating;
    @Size(max = 255)
    @jakarta.validation.constraints.Size(max = 2000)
    private String review;

    public ReviewRequest() {
    }

    public ReviewRequest(Integer rating, String review) {
        this.rating = rating;
        this.review = review;
    }
}
