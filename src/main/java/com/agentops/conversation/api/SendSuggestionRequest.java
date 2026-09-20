package com.agentops.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendSuggestionRequest(@NotBlank @Size(max = 128) String clientRequestId) {}
