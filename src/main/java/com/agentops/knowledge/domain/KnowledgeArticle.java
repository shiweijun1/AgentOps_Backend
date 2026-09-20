package com.agentops.knowledge.domain;

import com.agentops.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_article")
public class KnowledgeArticle extends BaseAuditableEntity {
    @Id @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "title", nullable = false, length = 255) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 32)
    private KnowledgeSourceType sourceType;
    @Column(name = "category_id", columnDefinition = "BINARY(16)") private UUID categoryId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32)
    private KnowledgeArticleStatus status;
    @Column(name = "current_version_id", columnDefinition = "BINARY(16)") private UUID currentVersionId;
    @Column(name = "valid_from") private Instant validFrom;
    @Column(name = "valid_until") private Instant validUntil;
    @Version @Column(name = "version", nullable = false) private long version;

    protected KnowledgeArticle() {}

    public static KnowledgeArticle create(String tenantId, String title, Instant validFrom, Instant validUntil) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.id = UUID.randomUUID(); article.tenantId = tenantId; article.title = title;
        article.sourceType = KnowledgeSourceType.MANUAL; article.status = KnowledgeArticleStatus.DRAFT;
        article.validFrom = validFrom; article.validUntil = validUntil;
        return article;
    }

    public void publish(UUID versionId) {
        if (status == KnowledgeArticleStatus.WITHDRAWN)
            throw new IllegalStateException("Withdrawn article cannot be published");
        currentVersionId = versionId; status = KnowledgeArticleStatus.PUBLISHED;
    }

    public void withdraw() {
        if (status != KnowledgeArticleStatus.PUBLISHED)
            throw new IllegalStateException("Only published article can be withdrawn");
        status = KnowledgeArticleStatus.WITHDRAWN;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public KnowledgeSourceType getSourceType() { return sourceType; }
    public KnowledgeArticleStatus getStatus() { return status; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public long getVersion() { return version; }
}
