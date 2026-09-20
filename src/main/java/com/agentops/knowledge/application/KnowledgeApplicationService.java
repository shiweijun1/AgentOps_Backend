package com.agentops.knowledge.application;

import com.agentops.knowledge.domain.*;
import com.agentops.knowledge.infrastructure.persistence.*;
import com.agentops.knowledge.infrastructure.search.MysqlKnowledgeSearch;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeApplicationService {
    private final KnowledgeArticleRepository articles;
    private final KnowledgeVersionRepository versions;
    private final KnowledgeChunkRepository chunks;
    private final MysqlKnowledgeSearch search;
    private final KnowledgeChunker chunker;
    private final Clock clock;

    public KnowledgeApplicationService(KnowledgeArticleRepository articles, KnowledgeVersionRepository versions,
                                       KnowledgeChunkRepository chunks, MysqlKnowledgeSearch search,
                                       KnowledgeChunker chunker, Clock clock) {
        this.articles = articles; this.versions = versions; this.chunks = chunks;
        this.search = search; this.chunker = chunker; this.clock = clock;
    }

    @Transactional
    public KnowledgeArticle createArticle(String tenantId, String title, Instant validFrom, Instant validUntil) {
        if (title == null || title.isBlank() || title.strip().length() > 255)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "标题不能为空且不能超过 255 字符");
        if (validFrom != null && validUntil != null && !validFrom.isBefore(validUntil))
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "有效期起点必须早于终点");
        return articles.saveAndFlush(KnowledgeArticle.create(tenantId, title.strip(), validFrom, validUntil));
    }

    @Transactional
    public KnowledgeVersion createVersion(UUID articleId, String tenantId, String content, String actorId) {
        KnowledgeArticle article = lockArticle(articleId, tenantId);
        if (article.getStatus() == KnowledgeArticleStatus.WITHDRAWN)
            throw new BusinessException(ErrorCode.CONFLICT, "已撤回文章不能新增版本");
        final String normalized = content == null ? "" : content.strip();
        try { chunker.split(normalized); }
        catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_REQUEST, exception.getMessage()); }
        int next = versions.maxVersionNo(tenantId, articleId) + 1;
        return versions.saveAndFlush(KnowledgeVersion.draft(tenantId, articleId, next,
                normalized, sha256(normalized), actorId, clock.instant()));
    }

    @Transactional
    public KnowledgeArticle publish(UUID articleId, UUID versionId, String tenantId, String actorId) {
        KnowledgeArticle article = lockArticle(articleId, tenantId);
        KnowledgeVersion target = versions.findByIdAndTenantIdAndArticleId(versionId, tenantId, articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (article.getStatus() == KnowledgeArticleStatus.PUBLISHED
                && versionId.equals(article.getCurrentVersionId())
                && target.getReviewStatus() == KnowledgeReviewStatus.PUBLISHED) return article;
        if (article.getStatus() == KnowledgeArticleStatus.WITHDRAWN
                || target.getReviewStatus() != KnowledgeReviewStatus.DRAFT)
            throw new BusinessException(ErrorCode.CONFLICT, "文章或版本状态不允许发布");

        List<KnowledgeChunker.Part> parts;
        try { parts = chunker.split(target.getContent()); }
        catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_REQUEST, exception.getMessage()); }
        Instant now = clock.instant();
        chunks.saveAllAndFlush(parts.stream().map(part -> new KnowledgeChunk(
                tenantId, versionId, part.index(), part.content(), part.estimatedTokenCount(), now)).toList());
        if (article.getCurrentVersionId() != null) {
            KnowledgeVersion previous = versions.findByIdAndTenantIdAndArticleId(
                    article.getCurrentVersionId(), tenantId, articleId).orElseThrow();
            previous.supersede();
        }
        target.publish(actorId, now);
        article.publish(versionId);
        versions.flush();
        articles.flush();
        return article;
    }

    @Transactional
    public KnowledgeArticle withdraw(UUID articleId, String tenantId) {
        KnowledgeArticle article = lockArticle(articleId, tenantId);
        if (article.getStatus() == KnowledgeArticleStatus.WITHDRAWN) return article;
        if (article.getStatus() != KnowledgeArticleStatus.PUBLISHED)
            throw new BusinessException(ErrorCode.CONFLICT, "只有已发布文章可以撤回");
        article.withdraw();
        articles.flush();
        return article;
    }

    @Transactional(readOnly = true)
    public KnowledgeArticle getArticle(UUID articleId, String tenantId) {
        return articles.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeVersion> listVersions(UUID articleId, String tenantId) {
        getArticle(articleId, tenantId);
        return versions.findByTenantIdAndArticleIdOrderByVersionNoDesc(tenantId, articleId);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchHit> search(String tenantId, String keyword, int limit) {
        String query = keyword == null ? "" : keyword.strip();
        int count = query.codePointCount(0, query.length());
        if (count < 2 || count > 100 || limit < 1 || limit > 50)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "关键词需为 2–100 个字符，limit 需为 1–50");
        return search.search(tenantId, query, clock.instant(), limit);
    }

    private KnowledgeArticle lockArticle(UUID id, String tenantId) {
        return articles.lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
