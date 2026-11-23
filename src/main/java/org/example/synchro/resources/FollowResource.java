package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Follow;
import org.example.synchro.repositories.FollowRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowResource {
    private final FollowRepository follows;
    private final UserRepository users;
    private final CookieService cookies;

    public record FollowStatus(boolean canFollow, boolean following, long followers, long followingCount) {}

    private record FollowPermission(boolean canFollow, boolean following) {}

    @GetMapping("/{targetId}")
    public ResponseEntity<FollowStatus> getStatus(@CookieValue(name = "token_jwt", required = false) String token,
                                                  @PathVariable Long targetId) {
        if (!users.existsById(targetId)) throw new ResourceNotFoundException("Usuário não encontrado");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(withCounts(targetId, status(token, targetId)));
    }

    @GetMapping("/check/{targetId}")
    public ResponseEntity<Boolean> check(@CookieValue(name = "token_jwt", required = false) String token,
                                         @PathVariable Long targetId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status(token, targetId).following());
    }

    @PostMapping("/{targetId}")
    @Transactional
    public FollowStatus follow(@CookieValue(name = "token_jwt", required = false) String token,
                               @PathVariable Long targetId) {
        var viewer = users.findByIdForUpdate(viewerForChange(token, targetId))
                .orElseThrow(() -> new UnauthorizedException("Sessão inválida"));
        var target = users.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        if (!follows.existsBySeguidorIdAndSeguidoId(viewer.getId(), targetId)) {
            follows.save(new Follow(viewer, target));
        }
        return withCounts(targetId, new FollowPermission(true, true));
    }

    @DeleteMapping("/{targetId}")
    @Transactional
    public FollowStatus unfollow(@CookieValue(name = "token_jwt", required = false) String token,
                                 @PathVariable Long targetId) {
        Long viewerId = viewerForChange(token, targetId);
        users.findByIdForUpdate(viewerId).orElseThrow(() -> new UnauthorizedException("Sessão inválida"));
        if (!users.existsById(targetId)) throw new ResourceNotFoundException("Usuário não encontrado");
        follows.findBySeguidorIdAndSeguidoId(viewerId, targetId).ifPresent(follows::delete);
        return withCounts(targetId, new FollowPermission(true, false));
    }

    private FollowStatus withCounts(Long targetId, FollowPermission permission) {
        return new FollowStatus(permission.canFollow(), permission.following(),
                follows.countBySeguidoId(targetId), follows.countBySeguidorId(targetId));
    }

    private FollowPermission status(String token, Long targetId) {
        try {
            if (!cookies.checkCookie(token)) return new FollowPermission(false, false);
            Long viewerId = cookies.getId(token);
            boolean canFollow = !viewerId.equals(targetId) && users.existsById(viewerId);
            return new FollowPermission(canFollow, canFollow && follows.existsBySeguidorIdAndSeguidoId(viewerId, targetId));
        } catch (InvalidTokenException | NumberFormatException ignored) {
            return new FollowPermission(false, false);
        }
    }

    private Long viewerForChange(String token, Long targetId) {
        if (!cookies.checkCookie(token)) throw new UnauthorizedException("Faça login para seguir perfis");
        Long viewerId = cookies.getId(token);
        if (viewerId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você não pode seguir seu próprio perfil");
        }
        return viewerId;
    }
}
