package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.*;
import org.example.synchro.dto.lastFmDtos.LastFmSessionDto;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Table(name = "last")
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class LastFmSession implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String sessionKey;
    private int subscriber;

    @OneToOne
    @JoinColumn(name = "synchro_user_id")
    private User synchroUser;

    public LastFmSession(LastFmSessionDto lastFmSessionDto) {
        this.name = lastFmSessionDto.getSession().getName();
        this.sessionKey = lastFmSessionDto.getSession().getKey();
        this.subscriber = lastFmSessionDto.getSession().getSubscriber();
    }
    public LastFmSession(String name, String key, int subscriber) {
        this.name = name;
        this.sessionKey = key;
        this.subscriber = subscriber;
    }

    public void setSynchroUser(User synchroUser) {
        this.synchroUser = synchroUser;
        synchroUser.setSession(this);
    }
}
