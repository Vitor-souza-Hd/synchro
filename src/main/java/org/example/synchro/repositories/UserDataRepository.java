package org.example.synchro.repositories;

import org.example.synchro.entities.UserData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDataRepository extends JpaRepository<UserData, Long> {
}
