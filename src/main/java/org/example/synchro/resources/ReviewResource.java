package org.example.synchro.resources;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.ProfileSelectionDto;
import org.example.synchro.dto.MidiaRatingDto;
import org.example.synchro.dto.RecentReviewDto;
import org.example.synchro.dto.ReviewDto;
import org.example.synchro.dto.ReviewRequest;
import org.example.synchro.services.ReviewService;
import org.springframework.http.CacheControl;
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
            @jakarta.validation.Valid @RequestBody ReviewRequest reviewRequest) {
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

    @GetMapping("/artists/{artistId}")
    public ResponseEntity<ReviewService.ArtistReviews> artistReviews(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable long artistId, @RequestParam(required = false) Long before) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.artistReviews(cookie, artistId, before == null ? Long.MAX_VALUE : before));
    }

    @PostMapping("/artists/{artistId}")
    public ReviewDto saveArtistReview(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable long artistId, @jakarta.validation.Valid @RequestBody ReviewRequest reviewRequest) {
        return reviewService.postArtistReview(cookie, reviewRequest, artistId);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<RecentReviewDto>> recent() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviewService.recentPublicReviews());
    }

    @GetMapping("/me/profile-selection")
    public ResponseEntity<ProfileSelectionDto> reviewSelection(@CookieValue(name = "token_jwt", required = false) String cookie) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviewService.reviewSelection(cookie));
    }

    @PutMapping("/me/profile-selection")
    public ResponseEntity<Void> saveReviewSelection(@CookieValue(name = "token_jwt", required = false) String cookie,
                                                     @Valid @RequestBody ProfileSelectionDto.Request request) {
        reviewService.saveReviewSelection(cookie, request.ids());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<List<RecentReviewDto>> reviewsOnProfile(@PathVariable long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviewService.reviewsOnProfile(userId));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ReviewService.PublicReviewPage> publicReviews(
            @PathVariable long userId, @RequestParam(required = false) Long before) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewService.publicReviewsByUser(userId, before == null ? Long.MAX_VALUE : before));
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
            @jakarta.validation.Valid @RequestBody ReviewRequest reviewRequest) {
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