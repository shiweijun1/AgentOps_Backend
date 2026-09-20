package com.agentops.conversation.infrastructure.persistence;

import com.agentops.conversation.domain.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {
    Optional<TicketMessage> findByTenantIdAndTicketIdAndClientRequestId(String tenantId, UUID ticketId, String requestId);
    List<TicketMessage> findByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(String tenantId, UUID ticketId);
    List<TicketMessage> findByTenantIdAndTicketIdAndVisibleToRequesterTrueOrderByCreatedAtAscIdAsc(
            String tenantId, UUID ticketId);
    long countByTenantIdAndTicketIdAndSourceSuggestionId(String tenantId, UUID ticketId, UUID suggestionId);
}
