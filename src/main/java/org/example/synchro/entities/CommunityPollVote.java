package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_community_poll_vote",
        uniqueConstraints = @UniqueConstraint(name = "uk_community_poll_vote_voter",
                columnNames = {"poll_id", "voter_id"}))
public class CommunityPollVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CommunityPoll poll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User voter;

    @Column(name = "option_index", nullable = false)
    private int optionIndex;

    public CommunityPollVote(CommunityPoll poll, User voter, int optionIndex) {
        this.poll = poll;
        this.voter = voter;
        this.optionIndex = optionIndex;
    }

    public void select(int optionIndex) {
        this.optionIndex = optionIndex;
    }
}
