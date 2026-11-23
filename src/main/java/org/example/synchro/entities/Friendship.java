package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_friendship",
        uniqueConstraints = @UniqueConstraint(columnNames = {"first_user_id", "second_user_id"}),
        indexes = @Index(name = "ix_friendship_second_user", columnList = "second_user_id"))
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_user_id", nullable = false)
    private User firstUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "second_user_id", nullable = false)
    private User secondUser;

    @Column(nullable = false)
    private boolean requestedByFirst;

    @Column(nullable = false)
    private boolean accepted;

    public Friendship(User firstUser, User secondUser, boolean requestedByFirst) {
        this.firstUser = firstUser;
        this.secondUser = secondUser;
        this.requestedByFirst = requestedByFirst;
    }

    public User otherUser(long viewerId) {
        return firstUser.getId() == viewerId ? secondUser : firstUser;
    }

    public boolean requestedBy(long viewerId) {
        long requesterId = requestedByFirst ? firstUser.getId() : secondUser.getId();
        return requesterId == viewerId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void accept() {
        accepted = true;
    }
}
