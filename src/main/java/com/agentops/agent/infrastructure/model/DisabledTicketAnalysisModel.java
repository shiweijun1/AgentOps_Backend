package com.agentops.agent.infrastructure.model;

import com.agentops.agent.application.TicketAnalysisModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agentops.ai", name = "provider", havingValue = "disabled")
public class DisabledTicketAnalysisModel implements TicketAnalysisModel {
    @Override public ModelResponse analyze(ModelRequest request) {
        throw new IllegalStateException("AI model disabled");
    }
    @Override public String provider() { return "disabled"; }
}
