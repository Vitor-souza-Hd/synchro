package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "tb_user")
@Getter
@Setter
public class User implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;
    private String password;
    private LocalDate birthDay;
    private String lastFmUsername;

    @OneToOne(mappedBy = "synchroUser")
    private LastFmSession session;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserData data;

    @OneToMany(mappedBy = "criador", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Playlist> playlistsCriadas = new HashSet<>();

    @ManyToMany(mappedBy = "usuarios")
    private Set<Playlist> playlists = new HashSet<>();

    public User(){

    }

    public User(Long id, String username, String email, String password, LocalDate birthDay, String lastFmUsername) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.birthDay = birthDay;
        this.lastFmUsername = lastFmUsername;
        this.data = new UserData(this);
    }


    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
