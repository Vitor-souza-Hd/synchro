package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UserSearchResult;
import org.example.synchro.entities.CommunityPost;
import org.example.synchro.entities.PostComment;
import org.example.synchro.entities.PostMentionNotification;
import org.example.synchro.entities.PostNotification;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.PostMentionNotificationRepository;
import org.example.synchro.repositories.PostNotificationRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostInteractionNotifications {
    private static final Pattern USERNAME = Pattern.compile(
            "(?<![\\p{L}\\p{M}\\p{N}_.+@-])@(?:\\{([^{}\\r\\n]{1,100})\\}|"
            + "([\\p{L}\\p{M}\\p{N}_.-]{1,100})(?![\\p{L}\\p{M}\\p{N}_.-]))");
    private static final int PAGE_SIZE = 12;
    private static final String MENTION_PREFIX = "mention-";
    private static final Instant END_OF_INBOX = Instant.parse("9999-12-31T23:59:59Z");

    private final PostNotificationRepository discussions;
    private final PostMentionNotificationRepository mentions;
    private final UserRepository users;

    public record NotificationView(Object id, Long postId, Long commentId, UserSearchResult actor,
                                   String type, Instant createdAt, boolean read) {}
    public record NotificationsPage(List<NotificationView> items, String nextBefore, long unread) {}
    public record UnreadCount(long unread) {}
    private record MentionName(String username, String alternate) {}
    private record InboxEntry(NotificationView view, boolean mention, long id) {
        Cursor cursor() {
            return new Cursor(view.createdAt(), mention, id);
        }
    }

    private record Cursor(Instant time, boolean mention, long id) {
        long beforeId(boolean mentionSource) {
            if (mention == mentionSource) return id;
            return mention ? Long.MAX_VALUE : 0;
        }

        String token() {
            return time + "|" + (mention ? "m" : "d") + "|" + id;
        }

        static Cursor parse(String value) {
            if (value == null) return new Cursor(END_OF_INBOX, true, Long.MAX_VALUE);
            String[] parts = value.split("\\|", -1);
            try {
                if (parts.length == 3 && ("m".equals(parts[1]) || "d".equals(parts[1]))) {
                    long id = Long.parseLong(parts[2]);
                    Instant time = Instant.parse(parts[0]);
                    if (id > 0 && !time.isBefore(Instant.EPOCH) && !time.isAfter(END_OF_INBOX)) {
                        return new Cursor(time, "m".equals(parts[1]), id);
                    }
                }
            } catch (NumberFormatException | DateTimeException ignored) {
                // Invalid cursors must not reach the database.
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página de notificações inválida.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyPostMentions(CommunityPost post) {
        notifyMentions(post, null, post.getAuthor(), post.getBody(), null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyCommentMentions(PostComment comment, User discussionRecipient) {
        notifyMentions(comment.getPost(), comment, comment.getAuthor(), comment.getBody(), discussionRecipient);
    }

    private void notifyMentions(CommunityPost post, PostComment comment, User actor, String body, User discussionRecipient) {
        var names = mentionedNames(body);
        if (names.isEmpty()) return;

        var keys = new HashSet<String>();
        for (var name : names) {
            keys.add(name.username());
            keys.add(name.alternate());
        }
        var matchingUsers = new HashMap<String, List<User>>();
        for (var user : users.findForMentions(keys)) {
            matchingUsers.computeIfAbsent(user.getUsername().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(user);
        }
        if (matchingUsers.isEmpty()) return;

        String content = comment == null ? "post:" + post.getId() : "comment:" + comment.getId();
        var notified = new HashSet<>(mentions.notifiedRecipients(content));
        notified.add(actor.getId());
        if (discussionRecipient != null) notified.add(discussionRecipient.getId());
        for (var name : names) {
            var candidates = matchingUsers.getOrDefault(name.username(),
                    matchingUsers.getOrDefault(name.alternate(), List.of()));
            if (candidates.size() == 1) {
                var user = candidates.get(0);
                if (notified.add(user.getId())) mentions.save(new PostMentionNotification(user, post, comment, content));
            }
        }
    }

    private static Set<MentionName> mentionedNames(String body) {
        var result = new LinkedHashSet<MentionName>();
        var matcher = USERNAME.matcher(body);
        while (matcher.find()) {
            boolean bracketed = matcher.group(1) != null;
            String name = matcher.group(bracketed ? 1 : 2).strip().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) result.add(new MentionName(name, bracketed ? name : name.replaceFirst("\\.+$", "")));
        }
        return result;
    }

    public NotificationsPage list(long viewer, String before) {
        var cursor = Cursor.parse(before);
        var request = PageRequest.of(0, PAGE_SIZE + 1);
        var entries = new ArrayList<InboxEntry>();
        discussions.inbox(viewer, cursor.time(), cursor.beforeId(false), request).stream()
                .map(PostInteractionNotifications::discussionEntry).forEach(entries::add);
        mentions.inbox(viewer, cursor.time(), cursor.beforeId(true), request).stream()
                .map(PostInteractionNotifications::mentionEntry).forEach(entries::add);
        entries.sort(Comparator.comparing((InboxEntry entry) -> entry.view().createdAt())
                .thenComparing(InboxEntry::mention).thenComparingLong(InboxEntry::id).reversed());
        var items = entries.stream().limit(PAGE_SIZE).map(InboxEntry::view).toList();
        String next = entries.size() > PAGE_SIZE ? entries.get(PAGE_SIZE - 1).cursor().token() : null;
        return new NotificationsPage(items, next, unread(viewer).unread());
    }

    public UnreadCount unread(long viewer) {
        return new UnreadCount(discussions.countByRecipientIdAndReadAtIsNull(viewer)
                + mentions.countByRecipientIdAndReadAtIsNull(viewer));
    }

    @Transactional
    public void markRead(long viewer, String key) {
        boolean mention = key.startsWith(MENTION_PREFIX);
        long id;
        try {
            id = Long.parseLong(mention ? key.substring(MENTION_PREFIX.length()) : key);
        } catch (NumberFormatException ignored) {
            throw missingNotification();
        }
        int updated = mention ? mentions.markRead(id, viewer, Instant.now()) : discussions.markRead(id, viewer, Instant.now());
        if (updated == 0) throw missingNotification();
    }

    @Transactional
    public void markAllRead(long viewer) {
        var now = Instant.now();
        discussions.markAllRead(viewer, now);
        mentions.markAllRead(viewer, now);
    }

    private static InboxEntry discussionEntry(PostNotification notification) {
        var comment = notification.getComment();
        var view = new NotificationView(notification.getId(), comment.getPost().getId(), comment.getId(),
                publicUser(comment.getAuthor()), notification.getType().name(), comment.getCreatedAt(),
                notification.getReadAt() != null);
        return new InboxEntry(view, false, notification.getId());
    }

    private static InboxEntry mentionEntry(PostMentionNotification notification) {
        var comment = notification.getComment();
        var post = notification.getPost();
        var actor = comment == null ? post.getAuthor() : comment.getAuthor();
        var view = new NotificationView(MENTION_PREFIX + notification.getId(), post.getId(),
                comment == null ? null : comment.getId(), publicUser(actor), "MENTION", notification.getCreatedAt(),
                notification.getReadAt() != null);
        return new InboxEntry(view, true, notification.getId());
    }

    static UserSearchResult publicUser(User user) {
        return new UserSearchResult(user.getId(), user.getUsername());
    }

    private static ResourceNotFoundException missingNotification() {
        return new ResourceNotFoundException("Notificação não encontrada.");
    }
}
