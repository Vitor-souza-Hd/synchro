package org.example.synchro.repositories;

import org.example.synchro.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    public User findByEmail(String email);
    public boolean existsByEmail(String email);
    public User findByUsername(String username);
    public Optional<User> findById(Long id);
}
