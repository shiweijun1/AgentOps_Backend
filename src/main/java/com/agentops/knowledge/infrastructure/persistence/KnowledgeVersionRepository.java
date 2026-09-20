package com.agentops.knowledge.infrastructure.persistence;

import com.agentops.knowledge.domain.KnowledgeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeVersionRepository extends JpaRepository<KnowledgeVersion, UUID> {
    Optional<KnowledgeVersion> findByIdAndTenantIdAndArticleId(UUID id, String tenantId, UUID articleId);
    List<KnowledgeVersion> findByTenantIdAndArticleIdOrderByVersionNoDesc(String tenantId, UUID articleId);

    @Query("select coalesce(max(v.versionNo), 0) from KnowledgeVersion v " +
            "where v.tenantId = :tenantId and v.articleId = :articleId")
    int maxVersionNo(@Param("tenantId") String tenantId, @Param("articleId") UUID articleId);
}
