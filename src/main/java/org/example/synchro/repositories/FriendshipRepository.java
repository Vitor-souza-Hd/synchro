package org.example.synchro.repositories;

import org.example.synchro.entities.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    Optional<Friendship> findByFirstUserIdAndSecondUserId(long firstUserId, long secondUserId);

    @Query("""
            SELECT friendship FROM Friendship friendship
            JOIN FETCH friendship.firstUser firstUser
            JOIN FETCH friendship.secondUser secondUser
            LEFT JOIN FETCH firstUser.data
            LEFT JOIN FETCH secondUser.data
            WHERE firstUser.id = :viewerId OR secondUser.id = :viewerId
            """)
    List<Friendship> findForUser(@Param("viewerId") long viewerId);
}
