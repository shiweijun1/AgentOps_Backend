package com.agentops.knowledge.api;

import jakarta.validation.constraints.NotBlank;

public record CreateKnowledgeVersionRequest(@NotBlank String content) {}
