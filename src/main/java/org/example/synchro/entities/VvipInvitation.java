package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "tb_vvip_invitation", indexes = {
        @Index(name = "ix_vvip_invitation_sender", columnList = "sender_id"),
        @Index(name = "ix_vvip_invitation_recipient", columnList = "recipient_id,status")
})
public class VvipInvitation {
    public enum Status { PENDING, ACCEPTED, DECLINED, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected VvipInvitation() {}

    public VvipInvitation(User sender, User recipient) {
        this.sender = sender;
        this.recipient = recipient;
    }

    public void finish(Status response) {
        this.status = response;
    }
}
