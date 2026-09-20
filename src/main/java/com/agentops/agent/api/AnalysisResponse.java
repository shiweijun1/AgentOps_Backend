package com.agentops.agent.api;

import com.agentops.agent.domain.*;
import com.agentops.ticket.domain.TicketPriority;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(UUID runId, UUID ticketId, CategoryCode categoryCode,
                               TicketPriority priority, Sentiment sentiment, RiskLevel riskLevel,
                               BigDecimal confidence, boolean manualRequired, String manualReason,
                               String reason, Instant createdAt) {
    public static AnalysisResponse from(AiAnalysisResult result) {
        return new AnalysisResponse(result.getRunId(), result.getTicketId(), result.getCategoryCode(),
                result.getSuggestedPriority(), result.getSentiment(), result.getRiskLevel(),
                result.getConfidence(), result.isManualRequired(), result.getManualReason(),
                result.getReason(), result.getCreatedAt());
    }
}
