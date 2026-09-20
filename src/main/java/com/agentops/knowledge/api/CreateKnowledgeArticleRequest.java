package com.agentops.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateKnowledgeArticleRequest(@NotBlank @Size(max = 255) String title,
                                            Instant validFrom, Instant validUntil) {}
