package org.example.synchro.entities;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "User_data")
public class UserData implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private Integer scrobbles;
    private Integer Artistas;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private User user;
}
