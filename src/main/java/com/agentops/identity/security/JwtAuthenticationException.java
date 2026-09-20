package com.agentops.identity.security;

import org.springframework.security.core.AuthenticationException;

public class JwtAuthenticationException extends AuthenticationException {

    public enum Reason {
        EXPIRED,
        INVALID
    }

    private final Reason reason;

    private JwtAuthenticationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static JwtAuthenticationException expired(Throwable cause) {
        return new JwtAuthenticationException(Reason.EXPIRED, "Access Token 已过期", cause);
    }

    public static JwtAuthenticationException invalid(Throwable cause) {
        return new JwtAuthenticationException(Reason.INVALID, "Access Token 无效", cause);
    }

    public Reason reason() {
        return reason;
    }
}
