package org.example.synchro.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.example.synchro.services.CommunityPollService;
import org.example.synchro.services.CommunityPollService.PollView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/{postId}/poll")
@RequiredArgsConstructor
public class CommunityPollResource {
    private final CommunityPollService polls;

    public record VoteRequest(@NotNull @PositiveOrZero Integer optionId) {}

    @PutMapping("/vote")
    public ResponseEntity<PollView> vote(@CookieValue(name = "token_jwt", required = false) String token,
                                         @PathVariable long postId, @Valid @RequestBody VoteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(polls.vote(token, postId, request.optionId()));
    }
}
