package com.agentops.ticket.application;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.ticket.domain.Ticket;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> visibleTo(
            AgentOpsPrincipal principal,
            TicketSearchCriteria criteria,
            boolean administrator
    ) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("tenantId"), principal.tenantId()));

            if (!administrator && !principal.permissions().contains("ticket:read:any")) {
                var visibility = new ArrayList<jakarta.persistence.criteria.Predicate>();
                if (principal.permissions().contains("ticket:read:self")) {
                    visibility.add(builder.equal(root.get("requesterId"), principal.userId()));
                }
                if (principal.permissions().contains("ticket:read:team")) {
                    visibility.add(builder.equal(root.get("assigneeId"), principal.userId()));
                    if (principal.teamId() != null) {
                        visibility.add(builder.equal(root.get("teamId"), principal.teamId()));
                    }
                }
                predicates.add(builder.or(visibility.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), criteria.priority()));
            }
            if (criteria.teamId() != null) {
                predicates.add(builder.equal(root.get("teamId"), criteria.teamId()));
            }
            if (criteria.assigneeId() != null) {
                predicates.add(builder.equal(root.get("assigneeId"), criteria.assigneeId()));
            }
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String like = "%" + criteria.keyword().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("ticketNo")), like),
                        builder.like(builder.lower(root.get("title")), like)
                ));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
