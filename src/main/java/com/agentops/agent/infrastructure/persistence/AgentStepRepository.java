package com.agentops.agent.infrastructure.persistence;

import com.agentops.agent.domain.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AgentStepRepository extends JpaRepository<AgentStep, UUID> {
    List<AgentStep> findByRunIdOrderBySequenceNo(UUID runId);
    void deleteByRunId(UUID runId);
}
