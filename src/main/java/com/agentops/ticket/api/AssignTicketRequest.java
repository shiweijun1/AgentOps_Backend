package com.agentops.ticket.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignTicketRequest(
        UUID teamId,
        UUID assigneeId,
        @NotBlank @Size(max = 500) String reason,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
