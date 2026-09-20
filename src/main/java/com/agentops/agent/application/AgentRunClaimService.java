package com.agentops.agent.application;

import com.agentops.agent.config.AgentProperties;
import com.agentops.agent.domain.AgentRunType;
import com.agentops.agent.infrastructure.persistence.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Service
public class AgentRunClaimService {
    private final AgentRunRepository runs;
    private final AgentProperties properties;
    private final Clock clock;

    public AgentRunClaimService(AgentRunRepository runs, AgentProperties properties, Clock clock) {
        this.runs = runs; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public List<Claim> claim() {
        return claim(properties.getBatchSize(), AgentRunType.TICKET_ANALYSIS);
    }

    @Transactional
    public Optional<Claim> claimOne() {
        return claim(1, AgentRunType.TICKET_ANALYSIS).stream().findFirst();
    }

    @Transactional
    public Optional<Claim> claimOne(AgentRunType runType) {
        return claim(1, runType).stream().findFirst();
    }

    private List<Claim> claim(int batchSize, AgentRunType runType) {
        Instant now = clock.instant();
        return runs.lockClaimable(now, batchSize, runType.name()).stream().map(run -> {
            String owner = UUID.randomUUID().toString();
            int changed = runs.claim(run.getId(), owner, now, now.plus(properties.getLeaseDuration()));
            if (changed != 1) throw new IllegalStateException("Agent run claim lost");
            return new Claim(run.getId(), run.getTicketId(), run.getTenantId(), owner,
                    run.getExecutionCount() + 1);
        }).toList();
    }

    public record Claim(UUID runId, UUID ticketId, String tenantId, String owner, int executionCount) {}
}
