package com.agentops.agent.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "knowledge_citation")
public class KnowledgeCitation {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "suggestion_id", nullable = false, columnDefinition = "BINARY(16)") private UUID suggestionId;
    @Column(name = "article_id", nullable = false, columnDefinition = "BINARY(16)") private UUID articleId;
    @Column(name = "knowledge_version_id", nullable = false, columnDefinition = "BINARY(16)") private UUID knowledgeVersionId;
    @Column(name = "chunk_id", nullable = false, columnDefinition = "BINARY(16)") private UUID chunkId;
    @Column(name = "retrieval_score", nullable = false, precision = 8, scale = 6) private BigDecimal retrievalScore;
    @Column(name = "rank_no", nullable = false) private int rankNo;
    @Column(name = "content_snapshot", nullable = false, columnDefinition = "TEXT") private String contentSnapshot;
    @Column(name = "used_in_answer", nullable = false) private boolean usedInAnswer;

    protected KnowledgeCitation() {}
    public KnowledgeCitation(String tenantId, UUID suggestionId, UUID articleId, UUID versionId,
                             UUID chunkId, double score, int rank, String content, boolean used) {
        id = UUID.randomUUID(); this.tenantId = tenantId; this.suggestionId = suggestionId;
        this.articleId = articleId; knowledgeVersionId = versionId; this.chunkId = chunkId;
        retrievalScore = BigDecimal.valueOf(Math.min(99.999999, Math.max(0, score)));
        rankNo = rank; contentSnapshot = content; usedInAnswer = used;
    }
    public UUID getId() { return id; }
    public UUID getSuggestionId() { return suggestionId; }
    public UUID getArticleId() { return articleId; }
    public UUID getKnowledgeVersionId() { return knowledgeVersionId; }
    public UUID getChunkId() { return chunkId; }
    public BigDecimal getRetrievalScore() { return retrievalScore; }
    public int getRankNo() { return rankNo; }
    public String getContentSnapshot() { return contentSnapshot; }
    public boolean isUsedInAnswer() { return usedInAnswer; }
}
