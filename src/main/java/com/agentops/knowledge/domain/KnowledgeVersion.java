package com.agentops.knowledge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_version")
public class KnowledgeVersion {
    @Id @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "article_id", nullable = false, columnDefinition = "BINARY(16)") private UUID articleId;
    @Column(name = "version_no", nullable = false) private int versionNo;
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT") private String content;
    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)") private String contentHash;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 32)
    private KnowledgeReviewStatus reviewStatus;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_by", length = 64) private String publishedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false, length = 64) private String createdBy;

    protected KnowledgeVersion() {}

    public static KnowledgeVersion draft(String tenantId, UUID articleId, int versionNo,
                                         String content, String contentHash, String actor, Instant now) {
        KnowledgeVersion version = new KnowledgeVersion();
        version.id = UUID.randomUUID(); version.tenantId = tenantId; version.articleId = articleId;
        version.versionNo = versionNo; version.content = content; version.contentHash = contentHash;
        version.reviewStatus = KnowledgeReviewStatus.DRAFT;
        version.createdAt = now; version.createdBy = actor;
        return version;
    }

    public void publish(String actor, Instant now) {
        if (reviewStatus != KnowledgeReviewStatus.DRAFT) throw new IllegalStateException("Version is not draft");
        reviewStatus = KnowledgeReviewStatus.PUBLISHED; publishedBy = actor; publishedAt = now;
    }

    public void supersede() {
        if (reviewStatus != KnowledgeReviewStatus.PUBLISHED) throw new IllegalStateException("Version is not published");
        reviewStatus = KnowledgeReviewStatus.SUPERSEDED;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getArticleId() { return articleId; }
    public int getVersionNo() { return versionNo; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public KnowledgeReviewStatus getReviewStatus() { return reviewStatus; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
