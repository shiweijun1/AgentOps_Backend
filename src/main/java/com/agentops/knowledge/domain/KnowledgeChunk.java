package com.agentops.knowledge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunk {
    @Id @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "knowledge_version_id", nullable = false, columnDefinition = "BINARY(16)") private UUID versionId;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    @Column(name = "content", nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "token_count", nullable = false) private int tokenCount;
    @Column(name = "token_count_estimated", nullable = false) private boolean tokenCountEstimated;
    @Column(name = "embedding_ref", length = 255) private String embeddingRef;
    @Enumerated(EnumType.STRING) @Column(name = "index_status", nullable = false, length = 32)
    private KnowledgeIndexStatus indexStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected KnowledgeChunk() {}
    public KnowledgeChunk(String tenantId, UUID versionId, int index, String content, int estimatedTokens, Instant now) {
        id = UUID.randomUUID(); this.tenantId = tenantId; this.versionId = versionId;
        chunkIndex = index; this.content = content; tokenCount = estimatedTokens;
        tokenCountEstimated = true; indexStatus = KnowledgeIndexStatus.INDEXED; createdAt = now;
    }
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getVersionId() { return versionId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public int getTokenCount() { return tokenCount; }
    public boolean isTokenCountEstimated() { return tokenCountEstimated; }
    public KnowledgeIndexStatus getIndexStatus() { return indexStatus; }
}
