package com.agentops.knowledge.api;

import com.agentops.knowledge.domain.*;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeArticleResponse(UUID id, String title, KnowledgeSourceType sourceType,
                                       KnowledgeArticleStatus status, UUID currentVersionId,
                                       Instant validFrom, Instant validUntil, long version,
                                       Instant createdAt, Instant updatedAt) {
    public static KnowledgeArticleResponse from(KnowledgeArticle article) {
        return new KnowledgeArticleResponse(article.getId(), article.getTitle(), article.getSourceType(),
                article.getStatus(), article.getCurrentVersionId(), article.getValidFrom(),
                article.getValidUntil(), article.getVersion(), article.getCreatedAt(), article.getUpdatedAt());
    }
}
