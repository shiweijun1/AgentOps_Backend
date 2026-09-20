package com.agentops.identity.api;

import com.agentops.identity.security.AgentOpsPrincipal;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String tenantId,
        String username,
        String displayName,
        String email,
        UUID teamId,
        Set<String> roles,
        Set<String> permissions
) {

    public static CurrentUserResponse from(AgentOpsPrincipal principal) {
        return new CurrentUserResponse(
                principal.userId(),
                principal.tenantId(),
                principal.username(),
                principal.displayName(),
                principal.email(),
                principal.teamId(),
                principal.roles(),
                principal.permissions()
        );
    }
}
