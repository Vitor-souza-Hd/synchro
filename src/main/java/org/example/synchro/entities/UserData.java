package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "User_data")
@Getter
@Setter
public class UserData implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private Integer scrobbles;
    private Integer artistas;
    private String username;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "author")
    private Set<Review> reviews = new HashSet<>();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private User user;

    public UserData(){}

    public UserData(User user) {
        this.user = user;
        username = user.getUsername();
    }

    public void addReview(Review review){
        reviews.add(review);
        review.setAuthor(this);
    }
    public void removeReview(Review review){
        reviews.remove(review);
        review.setAuthor(null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UserData userData = (UserData) o;
        return id == userData.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
