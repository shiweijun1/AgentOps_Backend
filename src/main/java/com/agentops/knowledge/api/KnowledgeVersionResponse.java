package com.agentops.knowledge.api;

import com.agentops.knowledge.domain.KnowledgeReviewStatus;
import com.agentops.knowledge.domain.KnowledgeVersion;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeVersionResponse(UUID id, UUID articleId, int versionNo, String content,
                                       KnowledgeReviewStatus reviewStatus, Instant publishedAt,
                                       String publishedBy, Instant createdAt) {
    public static KnowledgeVersionResponse from(KnowledgeVersion version) {
        return new KnowledgeVersionResponse(version.getId(), version.getArticleId(), version.getVersionNo(),
                version.getContent(), version.getReviewStatus(), version.getPublishedAt(),
                version.getPublishedBy(), version.getCreatedAt());
    }
}
