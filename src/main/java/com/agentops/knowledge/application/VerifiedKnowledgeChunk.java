package com.agentops.knowledge.application;

import java.util.UUID;

/** Authoritative source checked while shared database locks are held. */
public record VerifiedKnowledgeChunk(UUID articleId, UUID versionId, UUID chunkId,
                                     String title, String content) {}
