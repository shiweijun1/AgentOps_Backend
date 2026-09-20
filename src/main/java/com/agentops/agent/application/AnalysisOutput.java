package com.agentops.agent.application;

import com.agentops.agent.domain.CategoryCode;
import com.agentops.agent.domain.RiskLevel;
import com.agentops.agent.domain.Sentiment;
import com.agentops.ticket.domain.TicketPriority;

public record AnalysisOutput(CategoryCode categoryCode, TicketPriority priority, Sentiment sentiment,
                             RiskLevel riskLevel, Double confidence, Boolean manualRequired,
                             String manualReason, String reason) {
}
