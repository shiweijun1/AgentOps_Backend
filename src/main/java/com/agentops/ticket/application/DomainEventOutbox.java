package com.agentops.ticket.application;

import com.agentops.ticket.event.TicketCreatedEvent;

public interface DomainEventOutbox {

    void append(TicketCreatedEvent event);
}
