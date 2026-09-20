package com.agentops.agent.infrastructure.model;

import com.agentops.agent.application.TicketAnalysisModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agentops.ai", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeTicketAnalysisModel implements TicketAnalysisModel {
    @Override
    public ModelResponse analyze(ModelRequest request) {
        return new ModelResponse("""
                {"categoryCode":"OTHER","priority":"MEDIUM","sentiment":"NEUTRAL",
                 "riskLevel":"LOW","confidence":0.8,"manualRequired":false,
                 "manualReason":null,"reason":"Fake model deterministic analysis"}
                """, "fake-v1", 0, 0);
    }

    @Override public String provider() { return "fake"; }
}
