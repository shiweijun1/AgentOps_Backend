package com.agentops.agent.application;

import com.agentops.knowledge.application.KnowledgeSearchHit;
import java.util.List;

public interface ReplyDraftModel {
    ModelResponse draft(ModelRequest request) throws Exception;
    String provider();

    record ModelRequest(String title, String description, List<KnowledgeSearchHit> sources) {}
    record ModelResponse(String json, String modelName, int inputTokens, int outputTokens) {}
}
