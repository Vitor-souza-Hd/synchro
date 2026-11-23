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
@Table(name = "tb_community_poll_option",
        uniqueConstraints = @UniqueConstraint(name = "uk_community_poll_option_position",
                columnNames = {"poll_id", "option_index"}))
public class CommunityPollOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CommunityPoll poll;

    @Column(name = "option_index", nullable = false)
    private int position;

    @Column(name = "option_text", nullable = false, length = 80)
    private String text;

    CommunityPollOption(CommunityPoll poll, int position, String text) {
        this.poll = poll;
        this.position = position;
        this.text = text;
    }
}
