package org.example.synchro.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.synchro.services.CommunityPostService;
import org.example.synchro.services.CommunityPostService.Feed;
import org.example.synchro.services.CommunityPostService.FocusedPost;
import org.example.synchro.services.CommunityPostService.PostView;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class CommunityPostResource {
    private final CommunityPostService posts;

    public record AttachmentRequest(@NotNull @Pattern(regexp = "artist|album|track") String type,
                                    @NotNull @Positive Long id) {}
    public record PostRequest(@NotBlank @Size(max = CommunityPostService.MAX_LENGTH) String text,
                              @Valid AttachmentRequest attachment) {}

    @GetMapping
    public ResponseEntity<Feed> feed(@CookieValue(name = "token_jwt", required = false) String token,
                                     @RequestParam(required = false) Long before,
                                     @RequestParam(required = false) Long user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(posts.feed(token, before, user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FocusedPost> focusedPost(@CookieValue(name = "token_jwt", required = false) String token,
                                                   @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(posts.focusedPost(token, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostView publish(@CookieValue(name = "token_jwt", required = false) String token,
                            @Valid @RequestBody PostRequest request) {
        var attachment = request.attachment();
        return posts.publish(token, request.text(), attachment == null ? null : attachment.type(),
                attachment == null ? null : attachment.id());
    }

    @PutMapping("/{id}")
    public PostView edit(@CookieValue(name = "token_jwt", required = false) String token,
                         @PathVariable long id, @Valid @RequestBody PostRequest request) {
        var attachment = request.attachment();
        return posts.edit(token, id, request.text(), attachment == null ? null : attachment.type(),
                attachment == null ? null : attachment.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CookieValue(name = "token_jwt", required = false) String token, @PathVariable long id) {
        posts.delete(token, id);
    }
}
