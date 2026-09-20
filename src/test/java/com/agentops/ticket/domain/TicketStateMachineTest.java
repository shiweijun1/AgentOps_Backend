package com.agentops.ticket.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketStateMachineTest {

    @Test
    void shouldRejectIllegalTransition() {
        Ticket ticket = ticket();

        assertThatThrownBy(() -> ticket.transitionTo(TicketStatus.RESOLVED, false, Instant.now()))
                .isInstanceOf(IllegalTicketTransitionException.class);
    }

    @Test
    void shouldIncreaseReopenCountWhenResolvedTicketReturnsToProcessing() {
        Ticket ticket = processingTicket();
        ticket.transitionTo(TicketStatus.RESOLVED, false, Instant.parse("2030-01-01T00:00:00Z"));

        ticket.transitionTo(TicketStatus.PROCESSING, false, Instant.parse("2030-01-02T00:00:00Z"));

        assertThat(ticket.getReopenCount()).isEqualTo(1);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void closedTicketShouldOnlyBeReopenedByAdministrator() {
        Ticket ticket = processingTicket();
        ticket.transitionTo(TicketStatus.RESOLVED, false, Instant.now());
        ticket.transitionTo(TicketStatus.CLOSED, false, Instant.now());

        assertThatThrownBy(() -> ticket.transitionTo(TicketStatus.PROCESSING, false, Instant.now()))
                .isInstanceOf(IllegalTicketTransitionException.class);

        ticket.transitionTo(TicketStatus.PROCESSING, true, Instant.now());
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PROCESSING);
        assertThat(ticket.getReopenCount()).isEqualTo(1);
    }

    private Ticket processingTicket() {
        Ticket ticket = ticket();
        ticket.transitionTo(TicketStatus.PENDING, false, Instant.now());
        ticket.transitionTo(TicketStatus.PROCESSING, false, Instant.now());
        return ticket;
    }

    private Ticket ticket() {
        return Ticket.create(
                UUID.randomUUID(), "default", "T20300101-ABC", UUID.randomUUID(),
                "Title", "Description", TicketPriority.MEDIUM, "f".repeat(64), Instant.now()
        );
    }
}
