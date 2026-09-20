package com.agentops.agent.infrastructure.persistence;

import com.agentops.agent.domain.KnowledgeCitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KnowledgeCitationRepository extends JpaRepository<KnowledgeCitation, UUID> {
    List<KnowledgeCitation> findByTenantIdAndSuggestionIdOrderByRankNo(String tenantId, UUID suggestionId);
}
