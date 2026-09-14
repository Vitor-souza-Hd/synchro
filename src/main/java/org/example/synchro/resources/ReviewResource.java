package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.ReviewDto;
import org.example.synchro.dto.ReviewRequest;
import org.example.synchro.entities.Review;
import org.example.synchro.services.ReviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/review")
@RequiredArgsConstructor
public class ReviewResource {
    private final ReviewService reviewService;

   @PostMapping(value = "/midia/{midiaId}")
   public ReviewDto saveReview(@CookieValue(name = "token_jwt", required = false) String cookie, @PathVariable long midiaId, @RequestBody ReviewRequest reviewRequest){
       return reviewService.postReview(cookie,reviewRequest,midiaId);

   }
}
