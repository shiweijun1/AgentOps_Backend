package com.agentops.ticket.infrastructure.persistence;

import com.agentops.ticket.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByTenantIdAndRequesterIdAndOperationTypeAndIdempotencyKey(
            String tenantId,
            String requesterId,
            String operationType,
            String idempotencyKey
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO idempotency_record (
                id, tenant_id, requester_id, operation_type, idempotency_key,
                request_hash, status, expires_at, created_at, updated_at
            ) VALUES (
                UUID_TO_BIN(:id), :tenantId, :requesterId, :operationType, :idempotencyKey,
                :requestHash, 'PROCESSING', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 24 HOUR),
                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
            )
            """, nativeQuery = true)
    int reserve(@Param("id") String id,
                @Param("tenantId") String tenantId,
                @Param("requesterId") String requesterId,
                @Param("operationType") String operationType,
                @Param("idempotencyKey") String idempotencyKey,
                @Param("requestHash") String requestHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE idempotency_record
               SET status = 'COMPLETED', resource_type = 'TICKET', resource_id = :resourceId,
                   response_status = 201, updated_at = UTC_TIMESTAMP(6)
             WHERE tenant_id = :tenantId
               AND requester_id = :requesterId
               AND operation_type = :operationType
               AND idempotency_key = :idempotencyKey
            """, nativeQuery = true)
    int complete(@Param("tenantId") String tenantId,
                 @Param("requesterId") String requesterId,
                 @Param("operationType") String operationType,
                 @Param("idempotencyKey") String idempotencyKey,
                 @Param("resourceId") String resourceId);
}
