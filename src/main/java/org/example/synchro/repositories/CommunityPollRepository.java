package org.example.synchro.repositories;

import jakarta.persistence.LockModeType;
import org.example.synchro.entities.CommunityPoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityPollRepository extends JpaRepository<CommunityPoll, Long> {
    @Query("SELECT DISTINCT poll FROM CommunityPoll poll JOIN FETCH poll.options WHERE poll.post.id IN :postIds")
    List<CommunityPoll> findWithOptionsByPostIds(@Param("postIds") Collection<Long> postIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT poll FROM CommunityPoll poll WHERE poll.post.id = :postId")
    Optional<CommunityPoll> findByPostIdForUpdate(@Param("postId") long postId);
}
