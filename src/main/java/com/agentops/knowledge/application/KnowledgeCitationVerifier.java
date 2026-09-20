package com.agentops.knowledge.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KnowledgeCitationVerifier {
    /** Call inside the caller's write transaction; rows remain locked until commit. */
    List<VerifiedKnowledgeChunk> lockCurrent(String tenantId, List<UUID> chunkIds, Instant now);
}
