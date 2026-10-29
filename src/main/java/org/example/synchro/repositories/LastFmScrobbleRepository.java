package org.example.synchro.repositories;

import org.example.synchro.entities.LastFmScrobble;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LastFmScrobbleRepository extends JpaRepository<LastFmScrobble, String> {}
