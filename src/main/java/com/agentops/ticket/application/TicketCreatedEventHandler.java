package com.agentops.ticket.application;

import com.agentops.ticket.event.TicketCreatedEvent;

public interface TicketCreatedEventHandler {

    void handle(TicketCreatedEvent event);
}
