
package org.example.synchro.repositories;

import org.example.synchro.entities.FavoriteAlbum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteAlbumRepository
        extends JpaRepository<FavoriteAlbum, Long> {

    List<FavoriteAlbum>
    findByUserIdOrderByArtistNameAscAlbumTitleAsc(
            Long userId
    );

    Optional<FavoriteAlbum>
    findByUserIdAndReleaseGroupId(
            Long userId,
            String releaseGroupId
    );

    boolean existsByUserIdAndReleaseGroupId(
            Long userId,
            String releaseGroupId
    );
}
