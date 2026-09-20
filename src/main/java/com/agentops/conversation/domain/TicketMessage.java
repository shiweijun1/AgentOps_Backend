package com.agentops.conversation.domain;

import com.agentops.ticket.domain.TicketActorType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_message")
public class TicketMessage {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false) private String tenantId;
    @Column(name = "ticket_id", nullable = false, columnDefinition = "BINARY(16)", updatable = false) private UUID ticketId;
    @Enumerated(EnumType.STRING) @Column(name = "sender_type", nullable = false, length = 32, updatable = false)
    private TicketActorType senderType;
    @Column(name = "sender_id", nullable = false, length = 64, updatable = false) private String senderId;
    @Enumerated(EnumType.STRING) @Column(name = "message_type", nullable = false, length = 32, updatable = false)
    private TicketMessageType messageType;
    @Column(name = "content", nullable = false, columnDefinition = "TEXT", updatable = false) private String content;
    @Column(name = "source_suggestion_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID sourceSuggestionId;
    @Column(name = "client_request_id", nullable = false, length = 128, updatable = false)
    private String clientRequestId;
    @Column(name = "visible_to_requester", nullable = false, updatable = false) private boolean visibleToRequester;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected TicketMessage() {}
    public TicketMessage(String tenantId, UUID ticketId, TicketActorType senderType, UUID senderId,
            TicketMessageType messageType, String content, UUID sourceSuggestionId,
            String clientRequestId, boolean visibleToRequester, Instant createdAt) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.ticketId = ticketId;
        this.senderType = senderType; this.senderId = senderId.toString(); this.messageType = messageType;
        this.content = content; this.sourceSuggestionId = sourceSuggestionId;
        this.clientRequestId = clientRequestId; this.visibleToRequester = visibleToRequester;
        this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getTicketId() { return ticketId; }
    public TicketActorType getSenderType() { return senderType; }
    public String getSenderId() { return senderId; }
    public TicketMessageType getMessageType() { return messageType; }
    public String getContent() { return content; }
    public UUID getSourceSuggestionId() { return sourceSuggestionId; }
    public String getClientRequestId() { return clientRequestId; }
    public boolean isVisibleToRequester() { return visibleToRequester; }
    public Instant getCreatedAt() { return createdAt; }
}
