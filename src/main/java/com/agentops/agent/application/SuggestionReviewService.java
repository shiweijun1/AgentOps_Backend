package com.agentops.agent.application;

import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.*;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.knowledge.application.KnowledgeCitationVerifier;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.application.TicketAnalysisReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
public class SuggestionReviewService {
    private final AiSuggestionRepository suggestions;
    private final KnowledgeCitationRepository citations;
    private final KnowledgeCitationVerifier verifier;
    private final TicketAnalysisReader tickets;
    private final Clock clock;

    public SuggestionReviewService(AiSuggestionRepository suggestions, KnowledgeCitationRepository citations,
            KnowledgeCitationVerifier verifier, TicketAnalysisReader tickets, Clock clock) {
        this.suggestions = suggestions; this.citations = citations;
        this.verifier = verifier; this.tickets = tickets; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SuggestionView> list(UUID ticketId, AgentOpsPrincipal principal) {
        requireSupport(principal);
        tickets.readVisible(ticketId, principal);
        return suggestions.findByTenantIdAndTicketIdOrderByCreatedAtDesc(principal.tenantId(), ticketId)
                .stream().map(s -> view(s, principal.tenantId())).toList();
    }

    @Transactional(readOnly = true)
    public SuggestionView get(UUID id, AgentOpsPrincipal principal) {
        return view(visible(id, principal), principal.tenantId());
    }

    @Transactional
    public SuggestionView edit(UUID id, long expectedVersion, String content, AgentOpsPrincipal principal) {
        AiSuggestion suggestion = visible(id, principal);
        checkVersion(suggestion, expectedVersion);
        if (content == null || content.isBlank() || content.codePointCount(0, content.length()) > 2000)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "建议正文需为 1–2000 字符");
        requireCitationMarkers(content, suggestion, principal.tenantId());
        try { suggestion.edit(content.strip(), principal.userId().toString(), clock.instant()); }
        catch (IllegalStateException exception) { throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage()); }
        suggestions.flush();
        return view(suggestion, principal.tenantId());
    }

    @Transactional
    public SuggestionView adopt(UUID id, long expectedVersion, AgentOpsPrincipal principal) {
        AiSuggestion suggestion = visible(id, principal);
        checkVersion(suggestion, expectedVersion);
        if (tickets.lockForRunCreation(suggestion.getTicketId(), principal.tenantId()).inputRevision() != suggestion.getInputRevision())
            throw new BusinessException(ErrorCode.CONFLICT, "工单内容已变化，请重新分析");
        List<KnowledgeCitation> used = citations.findByTenantIdAndSuggestionIdOrderByRankNo(
                principal.tenantId(), id).stream().filter(KnowledgeCitation::isUsedInAnswer).toList();
        if (used.isEmpty()) throw new BusinessException(ErrorCode.CONFLICT, "建议没有有效引用");
        var verified = verifier.lockCurrent(principal.tenantId(), used.stream().map(KnowledgeCitation::getChunkId).toList(), clock.instant());
        if (verified.size() != used.size()) throw new BusinessException(ErrorCode.CONFLICT, "知识引用已失效，请重新生成建议");
        for (KnowledgeCitation citation : used) {
            if (verified.stream().noneMatch(chunk -> chunk.chunkId().equals(citation.getChunkId())
                    && chunk.articleId().equals(citation.getArticleId())
                    && chunk.versionId().equals(citation.getKnowledgeVersionId())
                    && chunk.content().equals(citation.getContentSnapshot())))
                throw new BusinessException(ErrorCode.CONFLICT, "知识引用已变更，请重新生成建议");
        }
        requireCitationMarkers(suggestion.getEditedContent() == null
                ? suggestion.getOriginalContent() : suggestion.getEditedContent(), suggestion, principal.tenantId());
        try { suggestion.adopt(principal.userId(), clock.instant()); }
        catch (IllegalStateException exception) { throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage()); }
        suggestions.flush();
        return view(suggestion, principal.tenantId());
    }

    @Transactional
    public SuggestionView reject(UUID id, long expectedVersion, String reason, AgentOpsPrincipal principal) {
        AiSuggestion suggestion = visible(id, principal);
        checkVersion(suggestion, expectedVersion);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "拒绝原因需为 1–500 字符");
        try { suggestion.reject(principal.userId().toString(), reason.strip(), clock.instant()); }
        catch (IllegalStateException exception) { throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage()); }
        suggestions.flush();
        return view(suggestion, principal.tenantId());
    }

    private AiSuggestion visible(UUID id, AgentOpsPrincipal principal) {
        requireSupport(principal);
        AiSuggestion suggestion = suggestions.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        tickets.readVisible(suggestion.getTicketId(), principal);
        return suggestion;
    }

    private void requireSupport(AgentOpsPrincipal principal) {
        if (!principal.roles().contains("ADMIN") && !principal.roles().contains("SUPPORT"))
            throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void checkVersion(AiSuggestion suggestion, long expected) {
        if (suggestion.getVersion() != expected)
            throw new BusinessException(ErrorCode.CONFLICT, "建议版本已变化，请刷新后重试");
    }

    private void requireCitationMarkers(String content, AiSuggestion suggestion, String tenantId) {
        Set<UUID> required = new HashSet<>();
        citations.findByTenantIdAndSuggestionIdOrderByRankNo(tenantId, suggestion.getId()).stream()
                .filter(KnowledgeCitation::isUsedInAnswer).forEach(c -> required.add(c.getChunkId()));
        if (required.isEmpty()) throw new BusinessException(ErrorCode.CONFLICT, "建议没有有效引用");
        Set<UUID> actual = new HashSet<>();
        var matcher = java.util.regex.Pattern.compile("\\[citation:([^\\]]+)\\]").matcher(content);
        try { while (matcher.find()) actual.add(UUID.fromString(matcher.group(1))); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "引用标记格式无效");
        }
        if (!actual.equals(required))
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "修改后的正文必须保留已验证的引用，且不能新增未经检索的引用");
    }

    private SuggestionView view(AiSuggestion suggestion, String tenantId) {
        return new SuggestionView(suggestion.getId(), suggestion.getTicketId(), suggestion.getRunId(),
                suggestion.getStatus(), suggestion.getOriginalContent(), suggestion.getEditedContent(),
                suggestion.getFinalContentSnapshot(), suggestion.getConfidence(), suggestion.getVersion(),
                citations.findByTenantIdAndSuggestionIdOrderByRankNo(tenantId, suggestion.getId()).stream()
                        .map(c -> new CitationView(c.getArticleId(), c.getKnowledgeVersionId(), c.getChunkId(),
                                c.getContentSnapshot(), c.getRetrievalScore(), c.isUsedInAnswer())).toList());
    }

    public record SuggestionView(UUID id, UUID ticketId, UUID runId, AiSuggestionStatus status,
            String originalContent, String editedContent, String finalContentSnapshot,
            java.math.BigDecimal confidence, long version, List<CitationView> citations) {}
    public record CitationView(UUID articleId, UUID versionId, UUID chunkId, String contentSnapshot,
            java.math.BigDecimal retrievalScore, boolean usedInAnswer) {}
}
