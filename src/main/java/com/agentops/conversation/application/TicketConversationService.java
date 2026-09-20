package com.agentops.conversation.application;

import com.agentops.agent.application.AdoptedSuggestionGateway;
import com.agentops.conversation.domain.TicketMessage;
import com.agentops.conversation.domain.TicketMessageType;
import com.agentops.conversation.infrastructure.persistence.TicketMessageRepository;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.application.TicketConversationAccess;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketActorType;
import com.agentops.ticket.domain.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TicketConversationService {
    private final TicketConversationAccess tickets;
    private final TicketMessageRepository messages;
    private final AdoptedSuggestionGateway suggestions;
    private final Clock clock;

    public TicketConversationService(TicketConversationAccess tickets, TicketMessageRepository messages,
            AdoptedSuggestionGateway suggestions, Clock clock) {
        this.tickets = tickets; this.messages = messages; this.suggestions = suggestions; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TicketMessage> list(UUID ticketId, AgentOpsPrincipal actor) {
        requirePermission(actor, "ticket:message:read");
        Ticket ticket = tickets.visible(ticketId, actor);
        if (isStaff(actor)) return messages.findByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(actor.tenantId(), ticketId);
        requireCustomerOwner(ticket, actor);
        return messages.findByTenantIdAndTicketIdAndVisibleToRequesterTrueOrderByCreatedAtAscIdAsc(
                actor.tenantId(), ticketId);
    }

    @Transactional
    public TicketMessage post(UUID ticketId, MessageIntent intent, String content,
            String clientRequestId, AgentOpsPrincipal actor) {
        if (intent == null) throw new BusinessException(ErrorCode.INVALID_REQUEST, "消息类型不能为空");
        String normalizedContent = content(content);
        String requestId = requestId(clientRequestId);
        Ticket ticket = tickets.lockVisible(ticketId, actor);
        boolean staff = isStaff(actor);
        TicketMessageType type;
        if (intent == MessageIntent.INTERNAL_NOTE) {
            requireStaff(staff); requirePermission(actor, "ticket:message:note");
            type = TicketMessageType.INTERNAL_NOTE;
        } else {
            requirePermission(actor, "ticket:message:reply");
            if (!staff) requireCustomerOwner(ticket, actor);
            type = staff ? TicketMessageType.SUPPORT_REPLY : TicketMessageType.CUSTOMER_REPLY;
        }
        TicketActorType sender = senderType(actor, staff);
        boolean publicMessage = type != TicketMessageType.INTERNAL_NOTE;
        var existing = messages.findByTenantIdAndTicketIdAndClientRequestId(actor.tenantId(), ticketId, requestId);
        if (existing.isPresent()) return sameRequest(existing.get(), sender, actor.userId(), type,
                normalizedContent, null, publicMessage);
        requireOpen(ticket);
        TicketMessage message = new TicketMessage(actor.tenantId(), ticketId, sender, actor.userId(),
                type, normalizedContent, null, requestId, publicMessage, now());
        messages.saveAndFlush(message);
        if (staff && publicMessage) ticket.recordFirstResponse(message.getCreatedAt());
        return message;
    }

    @Transactional
    public TicketMessage sendSuggestion(UUID ticketId, UUID suggestionId, String clientRequestId,
            AgentOpsPrincipal actor) {
        String requestId = requestId(clientRequestId);
        Ticket ticket = tickets.lockVisible(ticketId, actor);
        requireStaff(isStaff(actor));
        requirePermission(actor, "ticket:suggestion:send");
        var suggestion = suggestions.requireAdopted(suggestionId, ticketId, actor.tenantId());
        String content = content(suggestion.finalContentSnapshot());
        TicketActorType sender = senderType(actor, true);
        var existing = messages.findByTenantIdAndTicketIdAndClientRequestId(actor.tenantId(), ticketId, requestId);
        if (existing.isPresent()) return sameRequest(existing.get(), sender, actor.userId(),
                TicketMessageType.AI_SUGGESTION, content, suggestionId, true);
        if (suggestion.sourceMessageId() != null)
            throw new BusinessException(ErrorCode.CONFLICT, "该建议已经发送");
        requireOpen(ticket);
        TicketMessage message = new TicketMessage(actor.tenantId(), ticketId, sender, actor.userId(),
                TicketMessageType.AI_SUGGESTION, content, suggestionId, requestId, true, now());
        messages.saveAndFlush(message);
        suggestions.linkSentMessage(suggestionId, ticketId, actor.tenantId(), message.getId());
        ticket.recordFirstResponse(message.getCreatedAt());
        return message;
    }

    private TicketMessage sameRequest(TicketMessage existing, TicketActorType sender, UUID senderId,
            TicketMessageType type, String content, UUID suggestionId, boolean visible) {
        if (existing.getSenderType() != sender || !existing.getSenderId().equals(senderId.toString())
                || existing.getMessageType() != type || !existing.getContent().equals(content)
                || !Objects.equals(existing.getSourceSuggestionId(), suggestionId)
                || existing.isVisibleToRequester() != visible)
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "clientRequestId 已用于不同的消息或发送人");
        return existing;
    }

    private void requireCustomerOwner(Ticket ticket, AgentOpsPrincipal actor) {
        if (!actor.roles().contains("CUSTOMER") || !ticket.getRequesterId().equals(actor.userId()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void requireStaff(boolean staff) {
        if (!staff) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void requirePermission(AgentOpsPrincipal actor, String code) {
        if (!actor.permissions().contains(code)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private boolean isStaff(AgentOpsPrincipal actor) {
        return actor.roles().contains("ADMIN") || actor.roles().contains("SUPPORT");
    }
    private TicketActorType senderType(AgentOpsPrincipal actor, boolean staff) {
        return !staff ? TicketActorType.USER : actor.roles().contains("ADMIN")
                ? TicketActorType.ADMIN : TicketActorType.SUPPORT;
    }
    private void requireOpen(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.CLOSED)
            throw new BusinessException(ErrorCode.CONFLICT, "工单已关闭，请先重新打开");
    }
    private String content(String raw) {
        if (raw == null || raw.isBlank() || raw.codePointCount(0, raw.length()) > 4000)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "消息正文需为 1–4000 字符");
        return raw.strip();
    }
    private String requestId(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 128)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "clientRequestId 需为 1–128 字符");
        return raw.strip();
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
