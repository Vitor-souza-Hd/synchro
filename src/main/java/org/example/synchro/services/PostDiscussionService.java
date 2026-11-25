package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UserSearchResult;
import org.example.synchro.entities.PostComment;
import org.example.synchro.entities.PostNotification;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.CommunityPostRepository;
import org.example.synchro.repositories.PostCommentRepository;
import org.example.synchro.repositories.PostNotificationRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDiscussionService {
    public static final int MAX_COMMENT_LENGTH = 1000;
    private static final int PAGE_SIZE = 12;

    private final CommunityPostRepository posts;
    private final PostCommentRepository comments;
    private final PostNotificationRepository notifications;
    private final UserRepository users;
    private final CookieService cookies;
    private final PostInteractionNotifications interactionNotifications;

    public record CommentView(Long id, Long parentId, String body, UserSearchResult author,
                              UserSearchResult replyTo, Instant createdAt, boolean own, boolean deleted, Instant editedAt) {}
    public record CommentsPage(List<CommentView> items, Long nextBefore) {}
    public CommentsPage listComments(String token, long postId, Long before) {
        if (!posts.existsById(postId)) throw missingPost();
        var page = comments.findByPostIdAndIdLessThanOrderByIdDesc(postId, cursor(before), pageRequest());
        Long viewer = viewerId(token);
        return new CommentsPage(page.stream().limit(PAGE_SIZE).map(comment -> viewComment(comment, viewer)).toList(),
                page.size() > PAGE_SIZE ? page.get(PAGE_SIZE - 1).getId() : null);
    }

    public CommentView getComment(String token, long postId, long commentId) {
        return viewComment(findComment(postId, commentId), viewerId(token));
    }

    @Transactional
    public CommentView addComment(String token, long postId, Long parentId, String text) {
        long viewerId = requireViewerId(token);
        var author = users.findById(viewerId).orElseThrow(PostDiscussionService::invalidSession);
        // Serialize writes with deletion of the post.
        var post = posts.findByIdForUpdate(postId).orElseThrow(PostDiscussionService::missingPost);
        var parent = parentId == null ? null : findComment(postId, parentId);
        if (parent != null && parent.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este comentário foi excluído.");
        }
        var comment = comments.save(new PostComment(post, author, parent, text.strip()));
        User recipient = discussionRecipient(comment);
        if (!recipient.getId().equals(viewerId)) {
            var type = parent == null ? PostNotification.Type.COMMENT : PostNotification.Type.REPLY;
            notifications.save(new PostNotification(recipient, comment, type));
        }
        interactionNotifications.notifyCommentMentions(comment, recipient);
        return viewComment(comment, viewerId);
    }

    @Transactional
    public CommentView editComment(String token, long postId, long commentId, String text) {
        long viewer = requireViewerId(token);
        var comment = commentForChange(postId, commentId, viewer, false);
        if (comment.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Este comentário foi excluído.");
        }
        comment.edit(text.strip());
        interactionNotifications.notifyCommentMentions(comment, discussionRecipient(comment));
        return viewComment(comment, viewer);
    }

    @Transactional
    public void deleteComment(String token, long postId, long commentId) {
        commentForChange(postId, commentId, requireViewerId(token), true).delete();
    }

    public PostInteractionNotifications.NotificationsPage listNotifications(String token, String before) {
        return interactionNotifications.list(requireViewerId(token), before);
    }

    public PostInteractionNotifications.UnreadCount unreadNotifications(String token) {
        return interactionNotifications.unread(requireViewerId(token));
    }

    public void readNotification(String token, String id) {
        interactionNotifications.markRead(requireViewerId(token), id);
    }

    public void readAllNotifications(String token) {
        interactionNotifications.markAllRead(requireViewerId(token));
    }

    private PostComment commentForChange(long postId, long commentId, long viewer, boolean allowModeration) {
        posts.findByIdForUpdate(postId).orElseThrow(PostDiscussionService::missingPost);
        var comment = findComment(postId, commentId);
        if (!comment.getAuthor().getId().equals(viewer) && !(allowModeration && users.canModerate(viewer))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o autor pode alterar este comentário.");
        }
        return comment;
    }

    private static User discussionRecipient(PostComment comment) {
        return comment.getParent() == null ? comment.getPost().getAuthor() : comment.getParent().getAuthor();
    }

    private long requireViewerId(String token) {
        Long id = viewerId(token);
        if (id != null) return id;
        throw invalidSession();
    }

    private Long viewerId(String token) {
        try {
            return cookies.checkCookie(token) ? cookies.getId(token) : null;
        } catch (InvalidTokenException | NumberFormatException ignored) {
            return null;
        }
    }

    private PostComment findComment(long postId, long commentId) {
        return comments.findByPostIdAndId(postId, commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado nesta publicação."));
    }

    private static CommentView viewComment(PostComment comment, Long viewer) {
        var parent = comment.getParent();
        boolean deleted = comment.getDeletedAt() != null;
        var author = deleted ? null : PostInteractionNotifications.publicUser(comment.getAuthor());
        var replyTo = parent == null || parent.getDeletedAt() != null ? null
                : PostInteractionNotifications.publicUser(parent.getAuthor());
        return new CommentView(comment.getId(), parent == null ? null : parent.getId(), comment.getBody(),
                author, replyTo, comment.getCreatedAt(), !deleted && comment.getAuthor().getId().equals(viewer),
                deleted, comment.getEditedAt());
    }

    private static long cursor(Long before) {
        return before == null ? Long.MAX_VALUE : before;
    }

    private static PageRequest pageRequest() {
        return PageRequest.of(0, PAGE_SIZE + 1);
    }

    private static UnauthorizedException invalidSession() {
        return new UnauthorizedException("Entre na sua conta para interagir com as publicações.");
    }

    private static ResourceNotFoundException missingPost() {
        return new ResourceNotFoundException("Publicação não encontrada.");
    }
}
