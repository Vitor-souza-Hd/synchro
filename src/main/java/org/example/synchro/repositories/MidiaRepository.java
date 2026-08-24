package org.example.synchro.repositories;

import org.example.synchro.entities.Midia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MidiaRepository extends JpaRepository<Midia,Long> {

}

