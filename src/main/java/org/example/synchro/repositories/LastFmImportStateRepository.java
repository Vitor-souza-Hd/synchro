package org.example.synchro.repositories;

import org.example.synchro.entities.LastFmImportState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LastFmImportStateRepository extends JpaRepository<LastFmImportState, String> {}
