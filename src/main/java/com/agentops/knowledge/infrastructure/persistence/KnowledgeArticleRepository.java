package com.agentops.knowledge.infrastructure.persistence;

import com.agentops.knowledge.domain.KnowledgeArticle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {
    Optional<KnowledgeArticle> findByIdAndTenantId(UUID id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KnowledgeArticle a where a.id = :id and a.tenantId = :tenantId")
    Optional<KnowledgeArticle> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") String tenantId);
}
