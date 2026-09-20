package com.agentops.agent.infrastructure.model;

import com.agentops.agent.application.ReplyDraftModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agentops.ai", name = "provider", havingValue = "disabled")
public class DisabledReplyDraftModel implements ReplyDraftModel {
    @Override public ModelResponse draft(ModelRequest request) {
        throw new IllegalStateException("Reply model disabled; manual handling required");
    }
    @Override public String provider() { return "disabled"; }
}
