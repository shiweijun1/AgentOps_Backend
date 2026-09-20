package com.agentops.identity.api;

import java.time.Instant;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        Instant expiresAt
) {
}
