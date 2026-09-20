package com.agentops.agent.application;

public interface TicketAnalysisModel {
    ModelResponse analyze(ModelRequest request) throws Exception;
    String provider();

    record ModelRequest(String title, String description) {}
    record ModelResponse(String json, String modelName, int inputTokens, int outputTokens) {}
}
