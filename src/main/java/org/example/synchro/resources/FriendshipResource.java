package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.FriendshipService;
import org.example.synchro.services.FriendshipService.Action;
import org.example.synchro.services.FriendshipService.Connection;
import org.example.synchro.services.FriendshipService.State;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/friendships")
@RequiredArgsConstructor
public class FriendshipResource {
    private final FriendshipService friendships;
    private final CookieService cookies;

    public record Status(State state) {}

    @GetMapping
    public ResponseEntity<List<Connection>> list(@CookieValue(name = "token_jwt", required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(friendships.list(viewer(token)));
    }

    @GetMapping("/{targetId}")
    public ResponseEntity<Status> status(@CookieValue(name = "token_jwt", required = false) String token,
                                         @PathVariable long targetId) {
        return response(friendships.status(viewer(token), targetId));
    }

    @PostMapping("/{targetId}")
    public ResponseEntity<Status> request(@CookieValue(name = "token_jwt", required = false) String token,
                                          @PathVariable long targetId) {
        return response(friendships.change(viewer(token), targetId, Action.REQUEST));
    }

    @PatchMapping("/{targetId}")
    public ResponseEntity<Status> accept(@CookieValue(name = "token_jwt", required = false) String token,
                                         @PathVariable long targetId) {
        return response(friendships.change(viewer(token), targetId, Action.ACCEPT));
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<Status> remove(@CookieValue(name = "token_jwt", required = false) String token,
                                         @PathVariable long targetId, @RequestParam State state) {
        long viewerId = viewer(token);
        var action = switch (state) {
            case SENT -> Action.CANCEL;
            case RECEIVED -> Action.DECLINE;
            case FRIENDS -> Action.REMOVE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um pedido ou amizade.");
        };
        return response(friendships.change(viewerId, targetId, action));
    }

    private static ResponseEntity<Status> response(State state) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(state));
    }

    private long viewer(String token) {
        if (!cookies.checkCookie(token)) throw new UnauthorizedException("Entre para gerenciar amizades.");
        return cookies.getId(token);
    }
}
