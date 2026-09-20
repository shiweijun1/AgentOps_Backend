package com.agentops.analytics.api;

import com.agentops.ticket.domain.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record AnalyticsOverviewResponse(
        Instant from,
        Instant to,
        TicketMetrics tickets,
        AgentRunMetrics agentRuns,
        SuggestionMetrics suggestions
) {
    public record TicketMetrics(
            long createdCount,
            Map<TicketStatus, Long> currentStatusDistribution,
            long firstRespondedCount,
            BigDecimal averageFirstResponseMillis,
            long resolvedOrClosedCount,
            BigDecimal resolutionRate
    ) {}

    public record AgentRunMetrics(long completedCount, long succeededCount, BigDecimal successRate) {}

    public record SuggestionMetrics(
            long generatedCount,
            long adoptedCount,
            BigDecimal adoptionRate,
            long sentAdoptedCount,
            BigDecimal adoptedSendRate
    ) {}
}
