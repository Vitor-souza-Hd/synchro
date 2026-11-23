package org.example.synchro.repositories;

import jakarta.persistence.LockModeType;
import org.example.synchro.dto.UserSearchResult;
import org.example.synchro.entities.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    User findByEmail(String email);

    boolean existsByEmail(String email);

    User findByUsername(String username);

    @Query("SELECT account FROM User account WHERE LOWER(account.username) IN :names")
    List<User> findForMentions(@Param("names") Set<String> names);

    @Query("SELECT user FROM User user " +
            "WHERE LOWER(REPLACE(user.username, ' ', '')) = " +
            "LOWER(REPLACE(:username, ' ', ''))")
    List<User> findByUsernameIgnoringCaseAndSpaces(@Param("username") String username);

    default Slice<UserSearchResult> searchPeople(String query, Pageable pageable) {
        String term = query.strip();
        boolean usernameOnly = term.startsWith("@");
        if (usernameOnly) term = term.substring(1).strip();
        if (term.isEmpty()) return new SliceImpl<>(List.of(), pageable, false);
        return findPeople(term, usernameOnly, pageable);
    }

    @Query("""
            SELECT new org.example.synchro.dto.UserSearchResult(account.id, account.username, details.fullName)
            FROM User account LEFT JOIN account.data details
            WHERE LOCATE(LOWER(:term), LOWER(account.username)) > 0
               OR (:usernameOnly = false AND LOCATE(LOWER(:term), LOWER(details.fullName)) > 0)
            ORDER BY CASE
                WHEN LOWER(account.username) = LOWER(:term) THEN 0
                WHEN LOCATE(LOWER(:term), LOWER(account.username)) = 1 THEN 1
                ELSE 2 END, LOWER(account.username), account.id
            """)
    Slice<UserSearchResult> findPeople(@Param("term") String term, @Param("usernameOnly") boolean usernameOnly,
                                       Pageable pageable);


}
