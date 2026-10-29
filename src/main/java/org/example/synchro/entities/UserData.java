
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

    // Biografia personalizada do Synchro.
    @Column(name = "biography", length = 2000)
    private String biography;

    // URL da fotografia escolhida pelo usuario.
    // Null significa que nao existe foto personalizada.
    @Column(name = "profile_image_url", length = 2000)
    private String profileImageUrl;
    @Column(name = "banner_image_url", length = 2000)
    private String bannerImageUrl;

    @OneToMany(
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            mappedBy = "author"
    )
    private Set<Review> reviews = new HashSet<>();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private User user;

    public UserData() {
    }

    public UserData(User user) {
        this.user = user;
        this.username = user.getUsername();
        this.biography = null;
        this.profileImageUrl = null;
    }

    public void addReview(Review review) {
        reviews.add(review);
        review.setAuthor(this);
    }

    public void removeReview(Review review) {
        reviews.remove(review);
        review.setAuthor(null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserData userData = (UserData) o;
        return id == userData.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
