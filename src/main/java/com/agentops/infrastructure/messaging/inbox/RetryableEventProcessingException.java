package com.agentops.infrastructure.messaging.inbox;

public class RetryableEventProcessingException extends RuntimeException {

    public RetryableEventProcessingException(String message) {
        super(message);
    }
}
