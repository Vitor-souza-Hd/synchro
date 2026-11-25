package org.example.synchro.repositories;

import jakarta.persistence.LockModeType;
import org.example.synchro.dto.UserSearchResult;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserRole;
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

    @Query("select u.profileReviewSelection from User u where u.id = :id")
    String findProfileReviewSelection(@Param("id") long id);

    @Query("select u.profilePlaylistSelection from User u where u.id = :id")
    String findProfilePlaylistSelection(@Param("id") long id);

    boolean existsByIdAndRole(Long id, UserRole role);

    default boolean canModerate(Long id) {
        return id != null && existsByIdAndRole(id, UserRole.VVIP);
    }

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
        if (term.startsWith("@")) term = term.substring(1).strip();
        if (term.isEmpty()) return new SliceImpl<>(List.of(), pageable, false);
        return findPeople(term, pageable);
    }

    @Query("""
            SELECT new org.example.synchro.dto.UserSearchResult(account.id, account.username)
            FROM User account
            WHERE LOCATE(LOWER(:term), LOWER(account.username)) > 0
            ORDER BY CASE
                WHEN LOWER(account.username) = LOWER(:term) THEN 0
                WHEN LOCATE(LOWER(:term), LOWER(account.username)) = 1 THEN 1
                ELSE 2 END, LOWER(account.username), account.id
            """)
    Slice<UserSearchResult> findPeople(@Param("term") String term, Pageable pageable);


}
