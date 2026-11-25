package org.example.synchro.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.synchro.services.VvipInvitationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/vvip-invitations")
public class VvipInvitationResource {
    private final VvipInvitationService invitations;

    public record Invite(@NotBlank @Size(max = 200) String username) {}
    public record Answer(@NotNull Boolean accept) {}

    @GetMapping
    public ResponseEntity<VvipInvitationService.Page> list(@CookieValue(name = "token_jwt", required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.list(token));
    }

    @PostMapping
    public ResponseEntity<Void> invite(@CookieValue(name = "token_jwt", required = false) String token,
                                       @Valid @RequestBody Invite request) {
        invitations.invite(token, request.username());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@CookieValue(name = "token_jwt", required = false) String token, @PathVariable long id) {
        invitations.cancel(token, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> respond(@CookieValue(name = "token_jwt", required = false) String token,
                                        @PathVariable long id, @Valid @RequestBody Answer answer) {
        invitations.respond(token, id, answer.accept());
        return ResponseEntity.noContent().build();
    }
}
