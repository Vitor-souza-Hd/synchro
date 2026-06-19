package org.example.synchro.repositories;

import org.example.synchro.entities.Artista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtistaRepository extends JpaRepository<Artista, Long> {
    public Artista findByNome(String nome);
}
