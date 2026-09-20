package com.agentops.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TENANT_CLAIM = "tenant_id";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        if (properties.accessTokenTtl().isNegative() || properties.accessTokenTtl().isZero()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public IssuedToken issueAccessToken(AgentOpsPrincipal principal) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        String value = Jwts.builder()
                .issuer(properties.issuer())
                .subject(principal.userId().toString())
                .claim(TENANT_CLAIM, principal.tenantId())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(value, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public JwtIdentity parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .require(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new JwtIdentity(
                    UUID.fromString(claims.getSubject()),
                    claims.get(TENANT_CLAIM, String.class)
            );
        } catch (ExpiredJwtException exception) {
            throw JwtAuthenticationException.expired(exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw JwtAuthenticationException.invalid(exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

    public record JwtIdentity(UUID userId, String tenantId) {
    }
}
