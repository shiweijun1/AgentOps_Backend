package com.agentops.ticket.domain;

public class IllegalTicketTransitionException extends RuntimeException {

    private final TicketStatus from;
    private final TicketStatus to;

    public IllegalTicketTransitionException(TicketStatus from, TicketStatus to) {
        super("Illegal ticket transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public TicketStatus from() {
        return from;
    }

    public TicketStatus to() {
        return to;
    }
}
