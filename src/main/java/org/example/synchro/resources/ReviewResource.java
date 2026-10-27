package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.MidiaRatingDto;
import org.example.synchro.dto.ReviewDto;
import org.example.synchro.dto.ReviewRequest;
import org.example.synchro.services.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/review")
@RequiredArgsConstructor
public class ReviewResource {

    private final ReviewService reviewService;

    @PostMapping(value = "/midia/{midiaId}")
    public ReviewDto saveReview(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable long midiaId,
            @RequestBody ReviewRequest reviewRequest) {
        return reviewService.postReview(cookie, reviewRequest, midiaId);
    }

    // =====================================================
    // GET /review/me
    // =====================================================

    @GetMapping("/me")
    public ResponseEntity<List<ReviewDto>> minhasReviews(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String cookie
    ) {

        if (cookie == null || cookie.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        try {
            List<ReviewDto> reviews =
                    reviewService.listarMinhasReviews(cookie);

            return ResponseEntity.ok(reviews);

        } catch (
                org.example.synchro.services.exception
                        .InvalidTokenException error
        ) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping
    public List<ReviewDto> listarTodas() {
        return reviewService.listarTodas();
    }

    @GetMapping("/midia/{midiaId}/rating")
    public MidiaRatingDto getRating(@PathVariable Long midiaId) {
        return reviewService.getRatingDaMidia(midiaId);
    }

    // NOVO: 3 teste para a editar review
    @PutMapping("/{id}")
    public ReviewDto updateReview(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable Long id,
            @RequestBody ReviewRequest reviewRequest) {
        return reviewService.updateReview(cookie, id, reviewRequest);
    }

    // NOVO: excluir review
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable Long id) {
        reviewService.deleteReview(cookie, id);
        return ResponseEntity.noContent().build();
    }
}