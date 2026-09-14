package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.ReviewDto;
import org.example.synchro.dto.ReviewRequest;
import org.example.synchro.dto.Review_MusicaDto;
import org.example.synchro.entities.Midia;
import org.example.synchro.entities.Review;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.MidiaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.example.synchro.repositories.ReviewRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final MusicaRepository musicaRepository;
    private final MidiaRepository midiaRepository;
    private final CookieService cookieService;


    public ReviewDto postReview(String cookie, ReviewRequest review, Long midiaId){
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }
        Long userId = cookieService.getId(cookie);
        Optional<User> userOptional = userRepository.findById(userId);
        Optional<Midia> midiaOptional = midiaRepository.findById(midiaId);
        User user = userOptional.orElseThrow(()->new ResourceNotFoundException("User not found"));
        Midia midia =  midiaOptional.orElseThrow(()->new ResourceNotFoundException("Midia not found"));

        Review reviewEntity = new Review(review.getRating(),review.getReview(),midia,user.getData());
        reviewRepository.save(reviewEntity);

            return new ReviewDto(reviewEntity);
    }
}
