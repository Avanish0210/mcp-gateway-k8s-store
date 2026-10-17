package com.mcp_gateway_k8s_store.backend.repository;

import com.mcp_gateway_k8s_store.backend.model.TeamInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TeamInvitation i where i.tokenHash = :hash")
    Optional<TeamInvitation> findByTokenHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TeamInvitation i where i.id = :id")
    Optional<TeamInvitation> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TeamInvitation> findByActiveEmail(String activeEmail);

    List<TeamInvitation> findAllByOrderByCreatedAtDesc();
}
