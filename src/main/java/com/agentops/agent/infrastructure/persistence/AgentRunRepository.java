package com.agentops.agent.infrastructure.persistence;

import com.agentops.agent.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    Optional<AgentRun> findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(
            UUID ticketId, AgentRunType type, int revision, int attemptNo);
    List<AgentRun> findByTenantIdAndTicketIdOrderByCreatedAtDesc(String tenantId, UUID ticketId);
    Optional<AgentRun> findByIdAndTenantId(UUID id, String tenantId);

    @Query("select coalesce(max(r.attemptNo), 0) from AgentRun r where r.ticketId = :ticketId " +
            "and r.runType = :type and r.inputRevision = :revision")
    int maxAttempt(@Param("ticketId") UUID ticketId, @Param("type") AgentRunType type,
                   @Param("revision") int revision);

    @Query(value = """
            SELECT * FROM agent_run
             WHERE status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= :now)
             ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AgentRun> lockClaimable(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE agent_run SET status = 'RUNNING', worker_id = :owner, lease_until = :leaseUntil,
                execution_count = execution_count + 1,
                started_at = COALESCE(started_at, :now), updated_at = :now,
                error_code = NULL, error_message = NULL, version = version + 1
             WHERE id = :id AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= :now))
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("owner") String owner,
              @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
