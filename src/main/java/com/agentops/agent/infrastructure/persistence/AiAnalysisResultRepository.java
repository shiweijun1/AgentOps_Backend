package com.agentops.agent.infrastructure.persistence;

import com.agentops.agent.domain.AiAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, UUID> {
    Optional<AiAnalysisResult> findByRunId(UUID runId);
    Optional<AiAnalysisResult> findFirstByTenantIdAndTicketIdOrderByCreatedAtDesc(String tenantId, UUID ticketId);
}
