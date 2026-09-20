package com.agentops.ticket.api;

import java.util.List;

public record TicketPageResponse(
        List<TicketResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
