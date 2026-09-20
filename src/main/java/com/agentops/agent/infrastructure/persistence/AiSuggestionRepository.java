package com.agentops.agent.infrastructure.persistence;

import com.agentops.agent.domain.AiSuggestion;
import com.agentops.agent.domain.AiSuggestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {
    Optional<AiSuggestion> findByRunId(UUID runId);
    Optional<AiSuggestion> findByIdAndTenantId(UUID id, String tenantId);
    List<AiSuggestion> findByTenantIdAndTicketIdOrderByCreatedAtDesc(String tenantId, UUID ticketId);
    List<AiSuggestion> findByTenantIdAndTicketIdAndStatusIn(
            String tenantId, UUID ticketId, Collection<AiSuggestionStatus> statuses);
}
