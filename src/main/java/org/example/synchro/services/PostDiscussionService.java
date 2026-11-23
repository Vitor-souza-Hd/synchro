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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public record CommentView(Long id, Long parentId, String body, UserSearchResult author,
                              UserSearchResult replyTo, Instant createdAt) {}
    public record CommentsPage(List<CommentView> items, Long nextBefore) {}
    public record NotificationView(Long id, Long postId, Long commentId, UserSearchResult actor,
                                   PostNotification.Type type, Instant createdAt, boolean read) {}
    public record NotificationsPage(List<NotificationView> items, Long nextBefore, long unread) {}

    public CommentsPage listComments(long postId, Long before) {
        if (!posts.existsById(postId)) throw missingPost();
        var page = comments.findByPostIdAndIdLessThanOrderByIdDesc(postId, cursor(before), pageRequest());
        return new CommentsPage(page.stream().limit(PAGE_SIZE).map(PostDiscussionService::viewComment).toList(),
                page.size() > PAGE_SIZE ? page.get(PAGE_SIZE - 1).getId() : null);
    }

    public CommentView getComment(long postId, long commentId) {
        return viewComment(findComment(postId, commentId));
    }

    @Transactional
    public CommentView addComment(String token, long postId, Long parentId, String text) {
        long viewerId = requireViewerId(token);
        var author = users.findById(viewerId).orElseThrow(PostDiscussionService::invalidSession);
        // Serialize writes with deletion of the post.
        var post = posts.findByIdForUpdate(postId).orElseThrow(PostDiscussionService::missingPost);
        var parent = parentId == null ? null : findComment(postId, parentId);
        var comment = comments.save(new PostComment(post, author, parent, text.strip()));
        User recipient = parent == null ? post.getAuthor() : parent.getAuthor();
        if (!recipient.getId().equals(viewerId)) {
            var type = parent == null ? PostNotification.Type.COMMENT : PostNotification.Type.REPLY;
            notifications.save(new PostNotification(recipient, comment, type));
        }
        return viewComment(comment);
    }

    public NotificationsPage listNotifications(String token, Long before) {
        long viewerId = requireViewerId(token);
        var page = notifications.findByRecipientIdAndIdLessThanOrderByIdDesc(viewerId, cursor(before), pageRequest());
        return new NotificationsPage(page.stream().limit(PAGE_SIZE).map(PostDiscussionService::viewNotification).toList(),
                page.size() > PAGE_SIZE ? page.get(PAGE_SIZE - 1).getId() : null,
                notifications.countByRecipientIdAndReadAtIsNull(viewerId));
    }

    @Transactional
    public void readNotification(String token, long id) {
        if (notifications.markRead(id, requireViewerId(token), Instant.now()) == 0) {
            throw new ResourceNotFoundException("Notificação não encontrada.");
        }
    }

    @Transactional
    public void readAllNotifications(String token) {
        notifications.markAllRead(requireViewerId(token), Instant.now());
    }

    private long requireViewerId(String token) {
        try {
            if (cookies.checkCookie(token)) {
                Long id = cookies.getId(token);
                if (id != null) return id;
            }
        } catch (InvalidTokenException | NumberFormatException ignored) {
            // Treat invalid or expired sessions as signed out.
        }
        throw invalidSession();
    }

    private PostComment findComment(long postId, long commentId) {
        return comments.findByPostIdAndId(postId, commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado nesta publicação."));
    }

    private static CommentView viewComment(PostComment comment) {
        var parent = comment.getParent();
        return new CommentView(comment.getId(), parent == null ? null : parent.getId(), comment.getBody(),
                publicUser(comment.getAuthor()), parent == null ? null : publicUser(parent.getAuthor()),
                comment.getCreatedAt());
    }

    private static NotificationView viewNotification(PostNotification notification) {
        var comment = notification.getComment();
        return new NotificationView(notification.getId(), comment.getPost().getId(), comment.getId(),
                publicUser(comment.getAuthor()), notification.getType(), comment.getCreatedAt(),
                notification.getReadAt() != null);
    }

    private static UserSearchResult publicUser(User user) {
        var data = user.getData();
        return new UserSearchResult(user.getId(), user.getUsername(), data == null ? null : data.getFullName());
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
