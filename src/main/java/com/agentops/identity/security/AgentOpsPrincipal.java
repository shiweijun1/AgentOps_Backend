package com.agentops.identity.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record AgentOpsPrincipal(
        UUID userId,
        String tenantId,
        String username,
        String displayName,
        String email,
        UUID teamId,
        Set<String> roles,
        Set<String> permissions
) {

    public AgentOpsPrincipal {
        roles = Set.copyOf(new TreeSet<>(roles));
        permissions = Set.copyOf(new TreeSet<>(permissions));
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
