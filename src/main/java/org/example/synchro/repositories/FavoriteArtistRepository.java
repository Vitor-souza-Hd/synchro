
package org.example.synchro.repositories;

import org.example.synchro.entities.FavoriteArtist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteArtistRepository
        extends JpaRepository<FavoriteArtist, Long> {

    List<FavoriteArtist> findByUserIdOrderByArtistNameAsc(
            Long userId
    );

    Optional<FavoriteArtist> findByUserIdAndArtistKey(
            Long userId,
            String artistKey
    );

    boolean existsByUserIdAndArtistKey(
            Long userId,
            String artistKey
    );
}
