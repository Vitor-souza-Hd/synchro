package org.example.synchro.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.synchro.services.PostDiscussionService;
import org.example.synchro.services.PostDiscussionService.CommentView;
import org.example.synchro.services.PostDiscussionService.CommentsPage;
import org.example.synchro.services.PostDiscussionService.NotificationsPage;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PostDiscussionResource {
    private final PostDiscussionService discussions;

    public record CommentRequest(@NotBlank @Size(max = PostDiscussionService.MAX_COMMENT_LENGTH) String text,
                                 Long parentId) {}

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentsPage> comments(@PathVariable long postId,
                                                 @RequestParam(required = false) Long before) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discussions.listComments(postId, before));
    }

    @GetMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<CommentView> comment(@PathVariable long postId, @PathVariable long commentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discussions.getComment(postId, commentId));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView addComment(@CookieValue(name = "token_jwt", required = false) String token,
                                  @PathVariable long postId, @Valid @RequestBody CommentRequest request) {
        return discussions.addComment(token, postId, request.parentId(), request.text());
    }

    @GetMapping("/post-notifications")
    public ResponseEntity<NotificationsPage> notifications(
            @CookieValue(name = "token_jwt", required = false) String token,
            @RequestParam(required = false) Long before) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(discussions.listNotifications(token, before));
    }

    @PatchMapping("/post-notifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readNotification(@CookieValue(name = "token_jwt", required = false) String token,
                                 @PathVariable long id) {
        discussions.readNotification(token, id);
    }

    @PatchMapping("/post-notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAllNotifications(@CookieValue(name = "token_jwt", required = false) String token) {
        discussions.readAllNotifications(token);
    }
}
