package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_community_poll",
        uniqueConstraints = @UniqueConstraint(name = "uk_community_poll_post", columnNames = "post_id"))
public class CommunityPoll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CommunityPost post;

    @Column(nullable = false, length = 160)
    private String question;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.PERSIST)
    @OrderBy("position ASC")
    private List<CommunityPollOption> options = new ArrayList<>();

    public CommunityPoll(CommunityPost post, String question, List<String> options) {
        this.post = post;
        this.question = question;
        for (int index = 0; index < options.size(); index++) {
            this.options.add(new CommunityPollOption(this, index, options.get(index)));
        }
    }
}
