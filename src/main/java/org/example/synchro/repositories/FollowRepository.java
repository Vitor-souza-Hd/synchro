package org.example.synchro.repositories;

import org.example.synchro.entities.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    long countBySeguidoId(Long seguidoId);
    long countBySeguidorId(Long seguidorId);

    boolean existsBySeguidorIdAndSeguidoId(Long seguidorId, Long seguidoId);

    Optional<Follow> findBySeguidorIdAndSeguidoId(Long seguidorId, Long seguidoId);
}
