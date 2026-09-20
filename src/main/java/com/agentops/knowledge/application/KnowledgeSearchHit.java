package com.agentops.knowledge.application;

import java.util.UUID;

public record KnowledgeSearchHit(UUID articleId, UUID versionId, UUID chunkId,
                                 String title, String snippet, double score) {}
