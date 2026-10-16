package org.example.synchro.repositories;

import org.example.synchro.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);

    boolean existsByEmail(String email);

    User findByUsername(String username);

    @Query("SELECT user FROM User user " +
            "WHERE LOWER(REPLACE(user.username, ' ', '')) = " +
            "LOWER(REPLACE(:username, ' ', ''))")
    List<User> findByUsernameIgnoringCaseAndSpaces(@Param("username") String username);

    List<User> findByUsernameContainingIgnoreCase(String username);


}
