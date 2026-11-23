package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.PostInteractionService;
import org.example.synchro.services.PostInteractionService.Kind;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/{id}")
@RequiredArgsConstructor
public class PostInteractionResource {
    private final PostInteractionService interactions;

    @PutMapping("/{action:like|bookmark}")
    public ResponseEntity<PostInteractionService.View> add(
            @CookieValue(name = "token_jwt", required = false) String token,
            @PathVariable long id, @PathVariable String action) {
        return change(token, id, action, true);
    }

    @DeleteMapping("/{action:like|bookmark}")
    public ResponseEntity<PostInteractionService.View> remove(
            @CookieValue(name = "token_jwt", required = false) String token,
            @PathVariable long id, @PathVariable String action) {
        return change(token, id, action, false);
    }

    private ResponseEntity<PostInteractionService.View> change(String token, long id, String action, boolean active) {
        Kind kind = action.equals("like") ? Kind.LIKE : Kind.BOOKMARK;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(interactions.change(token, id, kind, active));
    }
}
