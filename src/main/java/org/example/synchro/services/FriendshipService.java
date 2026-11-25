package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UserSearchResult;
import org.example.synchro.entities.Friendship;
import org.example.synchro.repositories.FriendshipRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.ResourceConflictException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendshipService {
    private final FriendshipRepository friendships;
    private final UserRepository users;

    public enum State { SELF, NONE, SENT, RECEIVED, FRIENDS }
    public enum Action { REQUEST, ACCEPT, CANCEL, DECLINE, REMOVE }
    public record Connection(UserSearchResult user, State state) {}

    public List<Connection> list(long viewerId) {
        ensureViewer(viewerId);
        return friendships.findForUser(viewerId).stream().map(friendship -> {
            var other = friendship.otherUser(viewerId);
            var profile = new UserSearchResult(other.getId(), other.getUsername());
            return new Connection(profile, state(friendship, viewerId));
        }).sorted(Comparator.comparing(item -> item.user().username(), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public State status(long viewerId, long targetId) {
        ensureViewer(viewerId);
        if (viewerId == targetId) return State.SELF;
        if (!users.existsById(targetId)) throw new ResourceNotFoundException("Usuário não encontrado.");
        return state(find(viewerId, targetId), viewerId);
    }

    @Transactional
    public State change(long viewerId, long targetId, Action action) {
        if (viewerId == targetId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha outro perfil.");
        long firstId = Math.min(viewerId, targetId);
        long secondId = Math.max(viewerId, targetId);
        // Both request directions lock the same two accounts in the same order.
        var first = users.findByIdForUpdate(firstId).orElseThrow(() -> missingUser(firstId == viewerId));
        var second = users.findByIdForUpdate(secondId).orElseThrow(() -> missingUser(secondId == viewerId));
        var friendship = find(viewerId, targetId);

        return switch (action) {
            case REQUEST -> {
                if (friendship == null) {
                    friendships.save(new Friendship(first, second, viewerId == firstId));
                    yield State.SENT;
                }
                var current = state(friendship, viewerId);
                if (current == State.RECEIVED) {
                    throw new ResourceConflictException("Você já recebeu um pedido dessa pessoa. Aceite ou recuse o pedido.");
                }
                yield current;
            }
            case ACCEPT -> {
                if (friendship == null || friendship.requestedBy(viewerId)) {
                    throw new ResourceConflictException("Você não tem um pedido recebido dessa pessoa.");
                }
                friendship.accept();
                yield State.FRIENDS;
            }
            case CANCEL -> remove(friendship, viewerId, State.SENT);
            case DECLINE -> remove(friendship, viewerId, State.RECEIVED);
            case REMOVE -> remove(friendship, viewerId, State.FRIENDS);
        };
    }

    private State remove(Friendship friendship, long viewerId, State expected) {
        if (friendship == null) return State.NONE;
        if (state(friendship, viewerId) != expected) {
            throw new ResourceConflictException("A amizade ou o pedido mudou. Confira o estado atual.");
        }
        friendships.delete(friendship);
        return State.NONE;
    }

    private Friendship find(long first, long second) {
        return friendships.findByFirstUserIdAndSecondUserId(Math.min(first, second), Math.max(first, second)).orElse(null);
    }

    private static State state(Friendship friendship, long viewerId) {
        if (friendship == null) return State.NONE;
        if (friendship.isAccepted()) return State.FRIENDS;
        return friendship.requestedBy(viewerId) ? State.SENT : State.RECEIVED;
    }

    private void ensureViewer(long viewerId) {
        if (!users.existsById(viewerId)) throw missingUser(true);
    }

    private static RuntimeException missingUser(boolean viewer) {
        return viewer ? new UnauthorizedException("Sessão inválida.") : new ResourceNotFoundException("Usuário não encontrado.");
    }
}
