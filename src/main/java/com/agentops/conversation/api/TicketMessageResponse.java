package com.agentops.conversation.api;

import com.agentops.conversation.domain.TicketMessage;
import com.agentops.conversation.domain.TicketMessageType;
import com.agentops.ticket.domain.TicketActorType;
import java.time.Instant;
import java.util.UUID;

public record TicketMessageResponse(UUID id, UUID ticketId, TicketActorType senderType, String senderId,
        TicketMessageType messageType, String content, UUID sourceSuggestionId, String clientRequestId,
        boolean visibleToRequester, Instant createdAt) {
    public static TicketMessageResponse from(TicketMessage message) {
        return new TicketMessageResponse(message.getId(), message.getTicketId(), message.getSenderType(),
                message.getSenderId(), message.getMessageType(), message.getContent(),
                message.getSourceSuggestionId(), message.getClientRequestId(),
                message.isVisibleToRequester(), message.getCreatedAt());
    }
}
