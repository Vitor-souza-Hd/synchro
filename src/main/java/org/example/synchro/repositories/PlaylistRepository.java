package org.example.synchro.repositories;

import org.example.synchro.entities.Playlist;
import org.example.synchro.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByCriador(User criador);

    List<Playlist> findByColaboradoresContaining(User colaborador);
}
