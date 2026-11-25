package org.example.synchro.repositories;

import org.example.synchro.entities.LastFmProfileRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LastFmProfileRankingRepository extends JpaRepository<LastFmProfileRanking, Long> {
    Optional<LastFmProfileRanking> findByUserIdAndTypeAndPeriod(Long userId, String type, String period);

    void deleteByUserId(Long userId);
}
