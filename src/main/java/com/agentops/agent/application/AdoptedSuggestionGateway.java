package com.agentops.agent.application;

import java.util.UUID;

/** Conversation-facing application port; the caller owns the message transaction. */
public interface AdoptedSuggestionGateway {
    SendableSuggestion requireAdopted(UUID suggestionId, UUID ticketId, String tenantId);
    void linkSentMessage(UUID suggestionId, UUID ticketId, String tenantId, UUID messageId);

    record SendableSuggestion(UUID id, String finalContentSnapshot, UUID sourceMessageId) {}
}
