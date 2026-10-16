package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.MidiaRatingDto;
import org.example.synchro.dto.ReviewDto;
import org.example.synchro.dto.ReviewRequest;
import org.example.synchro.entities.Midia;
import org.example.synchro.entities.Review;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserData;
import org.example.synchro.repositories.MidiaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.example.synchro.repositories.ReviewRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.synchro.dto.MidiaRatingDto;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final MusicaRepository musicaRepository;
    private final MidiaRepository midiaRepository;
    private final CookieService cookieService;

    public ReviewDto postReview(
            String cookie,
            ReviewRequest review,
            Long midiaId
    ) {
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }

        Long userId = cookieService.getId(cookie);

        Optional<User> userOptional = userRepository.findById(userId);
        Optional<Midia> midiaOptional = midiaRepository.findById(midiaId);

        User user = userOptional.orElseThrow(
                () -> new ResourceNotFoundException("User not found")
        );

        Midia midia = midiaOptional.orElseThrow(
                () -> new ResourceNotFoundException("Midia not found")
        );

        UserData userData = user.getData();

        Optional<Review> reviewExistente =
                reviewRepository.findByAuthorIdAndMidiaId(
                        userData.getId(),
                        midiaId
                );

        Review reviewEntity;

        if (reviewExistente.isPresent()) {
            reviewEntity = reviewExistente.get();
            reviewEntity.setRating(review.getRating());
            reviewEntity.setReview(review.getReview());
        } else {
            reviewEntity = new Review(
                    review.getRating(),
                    review.getReview(),
                    midia,
                    userData
            );
        }

        reviewRepository.save(reviewEntity);

        return new ReviewDto(reviewEntity);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> listarTodas() {
        return reviewRepository.findAll()
                .stream()
                .map(ReviewDto::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public MidiaRatingDto buscarMediaPorMidia(Long midiaId) {

        Double media = reviewRepository.calcularMediaPorMidia(midiaId);
        Long total = reviewRepository.contarReviewsPorMidia(midiaId);

        return new MidiaRatingDto(
                midiaId,
                media != null
                        ? Math.round(media * 100.0) / 100.0
                        : 0.0,
                total != null ? total : 0L
        );

    }
    public MidiaRatingDto getRatingDaMidia(Long midiaId) {
        Double media = reviewRepository.calcularMediaPorMidia(midiaId);
        Long total = reviewRepository.contarReviewsPorMidia(midiaId);

        // Arredonda para 1 casa decimal
        Double mediaArredondada = (media != null)
                ? Math.round(media * 10.0) / 10.0
                : 0.0;

        Long totalFinal = (total != null) ? total : 0L;

        return new MidiaRatingDto(midiaId, mediaArredondada, totalFinal);
    }
    // 4 teste para atualizar/apagar review
    public ReviewDto updateReview(String cookie, Long reviewId, ReviewRequest request) {
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }

        Long userId = cookieService.getId(cookie);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        // Verifica se o usuário logado é o autor
        if (!review.getAuthor().getUser().getId().equals(userId)) {
            throw new InvalidTokenException("Você não é o autor desta review");
        }

        review.setRating(request.getRating());
        review.setReview(request.getReview());
        reviewRepository.save(review);

        return new ReviewDto(review);
    }

    public void deleteReview(String cookie, Long reviewId) {
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }

        Long userId = cookieService.getId(cookie);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getAuthor().getUser().getId().equals(userId)) {
            throw new InvalidTokenException("Você não é o autor desta review");
        }

        reviewRepository.delete(review);
    }
}
