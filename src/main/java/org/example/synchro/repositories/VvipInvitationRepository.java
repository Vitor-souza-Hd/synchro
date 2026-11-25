package org.example.synchro.repositories;

import org.example.synchro.entities.UserRole;
import org.example.synchro.entities.VvipInvitation;
import org.example.synchro.entities.VvipInvitation.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VvipInvitationRepository extends JpaRepository<VvipInvitation, Long> {
    interface Row {
        Long getId();
        String getUsername();
        Status getStatus();
        Instant getCreatedAt();
    }

    Optional<VvipInvitation> findByIdAndRecipientId(long id, long recipientId);

    @Query("select i.recipient.id from VvipInvitation i where i.id = :id and i.sender.id = :senderId")
    Optional<Long> recipientForSender(@Param("id") long id, @Param("senderId") long senderId);

    Optional<VvipInvitation> findFirstByRecipientIdAndStatusOrderByIdDesc(long recipientId, Status status);

    @Query("""
            select i.id as id, recipient.username as username, i.status as status, i.createdAt as createdAt
            from VvipInvitation i join i.recipient recipient
            where i.sender.id = :senderId
            order by i.id desc
            """)
    List<Row> outgoing(@Param("senderId") long senderId);

    @Query("""
            select i.id as id, sender.username as username, i.status as status, i.createdAt as createdAt
            from VvipInvitation i join i.sender sender
            where i.recipient.id = :recipientId and i.status = :status and sender.role = :role
            order by i.id desc
            """)
    List<Row> incoming(@Param("recipientId") long recipientId, @Param("status") Status status,
                       @Param("role") UserRole role);
}
