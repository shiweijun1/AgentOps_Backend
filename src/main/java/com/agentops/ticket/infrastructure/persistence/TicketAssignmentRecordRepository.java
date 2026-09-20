package com.agentops.ticket.infrastructure.persistence;

import com.agentops.ticket.domain.TicketAssignmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketAssignmentRecordRepository extends JpaRepository<TicketAssignmentRecord, UUID> {

    List<TicketAssignmentRecord> findByTenantIdAndTicketIdOrderByOccurredAtAsc(String tenantId, UUID ticketId);
}
