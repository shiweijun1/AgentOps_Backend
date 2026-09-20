package com.agentops.agent.application;

import com.agentops.agent.domain.AiSuggestion;
import com.agentops.agent.domain.AiSuggestionStatus;
import com.agentops.agent.infrastructure.persistence.AiSuggestionRepository;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class JpaAdoptedSuggestionGateway implements AdoptedSuggestionGateway {
    private final AiSuggestionRepository suggestions;
    public JpaAdoptedSuggestionGateway(AiSuggestionRepository suggestions) { this.suggestions = suggestions; }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public SendableSuggestion requireAdopted(UUID suggestionId, UUID ticketId, String tenantId) {
        AiSuggestion suggestion = requireSameTicket(suggestionId, ticketId, tenantId);
        if (suggestion.getStatus() != AiSuggestionStatus.ADOPTED
                || suggestion.getFinalContentSnapshot() == null || suggestion.getFinalContentSnapshot().isBlank())
            throw new BusinessException(ErrorCode.CONFLICT, "建议尚未采纳，不能发送");
        return new SendableSuggestion(suggestion.getId(), suggestion.getFinalContentSnapshot(),
                suggestion.getSourceMessageId());
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void linkSentMessage(UUID suggestionId, UUID ticketId, String tenantId, UUID messageId) {
        AiSuggestion suggestion = requireSameTicket(suggestionId, ticketId, tenantId);
        try { suggestion.markSent(messageId); }
        catch (IllegalStateException exception) { throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage()); }
        suggestions.flush();
    }

    private AiSuggestion requireSameTicket(UUID id, UUID ticketId, String tenantId) {
        AiSuggestion suggestion = suggestions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!suggestion.getTicketId().equals(ticketId))
            throw new BusinessException(ErrorCode.CONFLICT, "建议不属于当前工单");
        return suggestion;
    }
}
