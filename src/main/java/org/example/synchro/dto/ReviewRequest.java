package org.example.synchro.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @Min(value = 0, message = "valor deve estar no intervalo de 0 a 10")
    @Max(value = 10, message = "valor deve estar no intervalo de 0 a 10")
    private Integer rating;
    private String review;
}