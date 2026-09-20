package com.agentops.conversation.api;

import com.agentops.conversation.application.MessageIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostTicketMessageRequest(
        @NotNull MessageIntent intent,
        @NotBlank @Size(max = 4000) String content,
        @NotBlank @Size(max = 128) String clientRequestId
) {}
