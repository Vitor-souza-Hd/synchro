package org.example.synchro.repositories;

import org.example.synchro.entities.Follow;
import org.example.synchro.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findBySeguidorAndSeguido(User seguidor, User seguido);

    List<Follow> findBySeguidor(User seguidor);

    List<Follow> findBySeguido(User seguido);

    Long countBySeguido(User seguido);

    Long countBySeguidor(User seguidor);
}