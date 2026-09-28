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
    @Column(name = "search_text", nullable = false, columnDefinition = "TEXT") private String searchText;
    @Column(name = "section_path", nullable = false, length = 1000) private String sectionPath;
    @Column(name = "start_offset", nullable = false) private int startOffset;
    @Column(name = "end_offset", nullable = false) private int endOffset;
    @Enumerated(EnumType.STRING)
    @Column(name = "chunking_strategy", nullable = false, length = 32)
    private KnowledgeChunkingStrategy chunkingStrategy;
    @Column(name = "chunk_hash", nullable = false, columnDefinition = "CHAR(64)") private String chunkHash;
    @Column(name = "token_count", nullable = false) private int tokenCount;
    @Column(name = "token_count_estimated", nullable = false) private boolean tokenCountEstimated;
    @Enumerated(EnumType.STRING) @Column(name = "index_status", nullable = false, length = 32)
    private KnowledgeIndexStatus indexStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected KnowledgeChunk() {}
    public KnowledgeChunk(String tenantId, UUID versionId, int index, String content, String searchText,
                          String sectionPath, int startOffset, int endOffset,
                          KnowledgeChunkingStrategy strategy, String chunkHash,
                          int estimatedTokens, Instant now) {
        id = UUID.randomUUID(); this.tenantId = tenantId; this.versionId = versionId;
        chunkIndex = index; this.content = content; this.searchText = searchText;
        this.sectionPath = sectionPath; this.startOffset = startOffset; this.endOffset = endOffset;
        chunkingStrategy = strategy; this.chunkHash = chunkHash; tokenCount = estimatedTokens;
        tokenCountEstimated = true; indexStatus = KnowledgeIndexStatus.INDEXED; createdAt = now;
    }
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getVersionId() { return versionId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public String getSearchText() { return searchText; }
    public String getSectionPath() { return sectionPath; }
    public int getStartOffset() { return startOffset; }
    public int getEndOffset() { return endOffset; }
    public KnowledgeChunkingStrategy getChunkingStrategy() { return chunkingStrategy; }
    public String getChunkHash() { return chunkHash; }
    public int getTokenCount() { return tokenCount; }
    public boolean isTokenCountEstimated() { return tokenCountEstimated; }
    public KnowledgeIndexStatus getIndexStatus() { return indexStatus; }
}
