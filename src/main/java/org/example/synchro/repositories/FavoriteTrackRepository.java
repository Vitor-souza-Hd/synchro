
package org.example.synchro.repositories;

import org.example.synchro.entities.FavoriteTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteTrackRepository
        extends JpaRepository<FavoriteTrack, Long> {

    List<FavoriteTrack> findByUserIdOrderByArtistNameAscTrackNameAsc(
            Long userId
    );

    Optional<FavoriteTrack> findByUserIdAndTrackKey(
            Long userId,
            String trackKey
    );

    boolean existsByUserIdAndTrackKey(
            Long userId,
            String trackKey
    );
}
