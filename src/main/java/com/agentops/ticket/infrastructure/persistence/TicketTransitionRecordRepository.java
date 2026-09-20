package com.agentops.ticket.infrastructure.persistence;

import com.agentops.ticket.domain.TicketTransitionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTransitionRecordRepository extends JpaRepository<TicketTransitionRecord, UUID> {

    List<TicketTransitionRecord> findByTenantIdAndTicketIdOrderByOccurredAtAsc(String tenantId, UUID ticketId);

    Optional<TicketTransitionRecord> findByTenantIdAndTicketIdAndCommandId(
            String tenantId,
            UUID ticketId,
            String commandId
    );
}
