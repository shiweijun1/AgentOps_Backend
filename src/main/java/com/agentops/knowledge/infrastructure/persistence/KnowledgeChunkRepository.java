package com.agentops.knowledge.infrastructure.persistence;

import com.agentops.knowledge.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {
    List<KnowledgeChunk> findByTenantIdAndVersionIdOrderByChunkIndex(String tenantId, UUID versionId);
    long countByTenantIdAndVersionId(String tenantId, UUID versionId);
}
