package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.AuditLog;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByUserIdOrderByTimestampAsc(UUID userId);

    @Modifying
    @Query(value = """
            UPDATE audit_logs
            SET user_id = :anonymousUserId,
                resource_id = NULL,
                details = '{}'::jsonb,
                ip_address = NULL
            WHERE user_id = :userId
            """, nativeQuery = true)
    int anonymizeUserData(@Param("userId") UUID userId, @Param("anonymousUserId") UUID anonymousUserId);
}