package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserRole;
import org.example.synchro.entities.VvipInvitation;
import org.example.synchro.entities.VvipInvitation.Status;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.repositories.VvipInvitationRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceConflictException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VvipInvitationService {
    private final VvipInvitationRepository invitations;
    private final UserRepository users;
    private final CookieService cookies;

    public record Entry(Long id, String username, Status status, Instant createdAt) {}
    public record Page(boolean canInvite, List<Entry> incoming, List<Entry> outgoing) {}

    public Page list(String token) {
        long viewer = viewerId(token);
        if (!users.existsById(viewer)) throw invalidSession();
        boolean allowed = users.canModerate(viewer);
        return new Page(allowed, entries(invitations.incoming(viewer, Status.PENDING, UserRole.VVIP)),
                allowed ? entries(invitations.outgoing(viewer)) : List.of());
    }

    @Transactional
    public void invite(String token, String username) {
        long sender = viewerId(token);
        requireInviter(sender);
        String handle = username.strip();
        if (handle.startsWith("@")) handle = handle.substring(1).strip();
        var matches = users.findByUsernameIgnoringCaseAndSpaces(handle);
        if (handle.isBlank() || matches.size() != 1) {
            throw new ResourceNotFoundException("Conta não encontrada. Confira o @usuário.");
        }
        long recipientId = matches.get(0).getId();
        if (recipientId == sender) throw new ResourceConflictException("Escolha outra conta.");
        User recipient = lockRecipient(recipientId);
        requireInviter(sender);
        if (users.canModerate(recipientId)) throw new ResourceConflictException("Essa conta já é VVIP.");
        var pending = invitations.findFirstByRecipientIdAndStatusOrderByIdDesc(recipientId, Status.PENDING);
        if (pending.isPresent()) {
            var previous = pending.get();
            long previousSender = previous.getSender().getId();
            if (previousSender == sender) return;
            if (users.canModerate(previousSender)) {
                throw new ResourceConflictException("Essa conta já tem um convite VVIP pendente.");
            }
            previous.finish(Status.CANCELED);
        }
        invitations.save(new VvipInvitation(users.getReferenceById(sender), recipient));
    }

    @Transactional
    public void respond(String token, long invitationId, boolean accept) {
        long viewer = viewerId(token);
        User recipient = lockRecipient(viewer);
        var invitation = invitations.findByIdAndRecipientId(invitationId, viewer)
                .orElseThrow(() -> new ResourceNotFoundException("Convite VVIP não encontrado."));
        Status response = accept ? Status.ACCEPTED : Status.DECLINED;
        if (invitation.getStatus() == response) return;
        if (invitation.getStatus() != Status.PENDING) {
            throw new ResourceConflictException("Este convite já foi respondido.");
        }
        if (accept) {
            requireInviter(invitation.getSender().getId());
            recipient.setRole(UserRole.VVIP);
        }
        invitation.finish(response);
    }

    @Transactional
    public void cancel(String token, long invitationId) {
        long sender = viewerId(token);
        long recipientId = invitations.recipientForSender(invitationId, sender)
                .orElseThrow(() -> new ResourceNotFoundException("Convite VVIP não encontrado."));
        lockRecipient(recipientId);
        var invitation = invitations.findByIdAndRecipientId(invitationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Convite VVIP não encontrado."));
        if (invitation.getStatus() == Status.CANCELED) return;
        if (invitation.getStatus() != Status.PENDING) {
            throw new ResourceConflictException("Somente convites pendentes podem ser cancelados.");
        }
        invitation.finish(Status.CANCELED);
    }

    private static List<Entry> entries(List<VvipInvitationRepository.Row> rows) {
        return rows.stream().map(row -> new Entry(row.getId(), row.getUsername(), row.getStatus(), row.getCreatedAt())).toList();
    }

    private User lockRecipient(long id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
    }

    private void requireInviter(long id) {
        if (!users.canModerate(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O envio e a concessão de convites exigem um VVIP ativo.");
        }
    }

    private long viewerId(String token) {
        try {
            if (cookies.checkCookie(token)) return cookies.getId(token);
        } catch (InvalidTokenException | NumberFormatException ignored) {
            // Revoked or invalid sessions cannot access invitations.
        }
        throw invalidSession();
    }

    private static UnauthorizedException invalidSession() {
        return new UnauthorizedException("Entre para acessar convites VVIP.");
    }
}
