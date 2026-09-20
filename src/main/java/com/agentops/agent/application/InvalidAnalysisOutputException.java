package com.agentops.agent.application;

public class InvalidAnalysisOutputException extends RuntimeException {
    public InvalidAnalysisOutputException(Throwable cause) { super("Model returned invalid analysis JSON", cause); }
}
