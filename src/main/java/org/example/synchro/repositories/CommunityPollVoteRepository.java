package org.example.synchro.repositories;

import org.example.synchro.entities.CommunityPollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityPollVoteRepository extends JpaRepository<CommunityPollVote, Long> {
    interface VoteCount {
        Long getPollId();
        int getOptionIndex();
        long getVoteCount();
    }

    interface ViewerVote {
        Long getPollId();
        int getOptionIndex();
    }

    @Query("""
            SELECT vote.poll.id AS pollId, vote.optionIndex AS optionIndex, COUNT(vote) AS voteCount
            FROM CommunityPollVote vote WHERE vote.poll.id IN :pollIds
            GROUP BY vote.poll.id, vote.optionIndex
            """)
    List<VoteCount> countByPollAndOption(@Param("pollIds") Collection<Long> pollIds);

    @Query("""
            SELECT vote.poll.id AS pollId, vote.optionIndex AS optionIndex
            FROM CommunityPollVote vote WHERE vote.poll.id IN :pollIds AND vote.voter.id = :viewerId
            """)
    List<ViewerVote> findSelections(@Param("pollIds") Collection<Long> pollIds, @Param("viewerId") Long viewerId);

    Optional<CommunityPollVote> findByPollIdAndVoterId(Long pollId, Long voterId);
}
