package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.PostInteraction;
import org.example.synchro.repositories.CommunityPostRepository;
import org.example.synchro.repositories.PostInteractionRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostInteractionService {
    private final PostInteractionRepository interactions;
    private final CommunityPostRepository posts;
    private final UserRepository users;
    private final CookieService cookies;

    public enum Kind { LIKE, BOOKMARK }
    public record View(long likes, boolean liked, boolean saved) {}
    public static final View EMPTY = new View(0, false, false);

    public Map<Long, View> views(Collection<Long> postIds, Long viewer) {
        if (postIds.isEmpty()) return Map.of();
        Map<Long, View> result = new HashMap<>();
        interactions.countLikes(postIds).forEach(row ->
                result.put(row.getPostId(), new View(row.getTotal(), false, false)));
        if (viewer != null) {
            interactions.findByUserIdAndPostIdIn(viewer, postIds).forEach(item -> {
                long id = item.getPost().getId();
                long count = result.getOrDefault(id, EMPTY).likes();
                result.put(id, new View(count, item.isLiked(), item.isSaved()));
            });
        }
        return result;
    }

    @Transactional
    public View change(String token, long postId, Kind kind, boolean active) {
        long viewer = requireViewer(token);
        var user = users.findById(viewer).orElseThrow(() -> new UnauthorizedException("Sessão inválida."));
        var post = posts.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Publicação não encontrada."));
        var previous = interactions.findByPostIdAndUserId(postId, viewer);
        var item = previous.orElseGet(() -> new PostInteraction(post, user));
        if (kind == Kind.LIKE) item.setLiked(active);
        else item.setSaved(active);
        if (item.isLiked() || item.isSaved()) interactions.save(item);
        else previous.ifPresent(interactions::delete);
        return views(java.util.List.of(postId), viewer).getOrDefault(postId, EMPTY);
    }

    private long requireViewer(String token) {
        try {
            if (cookies.checkCookie(token)) return cookies.getId(token);
        } catch (InvalidTokenException | NumberFormatException ignored) {
            // An expired session is treated like a signed-out visitor.
        }
        throw new UnauthorizedException("Entre na sua conta para interagir com publicações.");
    }
}
