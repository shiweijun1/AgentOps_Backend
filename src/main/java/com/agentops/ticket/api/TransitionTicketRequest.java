package com.agentops.ticket.api;

import com.agentops.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TransitionTicketRequest(
        @NotNull TicketStatus toStatus,
        @Size(max = 500) String reason,
        @NotBlank @Size(max = 128) String commandId,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
