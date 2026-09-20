package com.agentops.ticket.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class TicketNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private final Clock clock;

    public TicketNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "T" + DATE.format(clock.instant()) + "-" + random;
    }
}
