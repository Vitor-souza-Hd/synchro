package org.example.synchro.repositories;

import org.example.synchro.entities.LastFmSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LastFmSessionRepository extends JpaRepository<LastFmSession, Long> {
}
