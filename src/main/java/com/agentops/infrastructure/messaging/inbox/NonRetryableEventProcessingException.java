package com.agentops.infrastructure.messaging.inbox;

public class NonRetryableEventProcessingException extends RuntimeException {

    public NonRetryableEventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
