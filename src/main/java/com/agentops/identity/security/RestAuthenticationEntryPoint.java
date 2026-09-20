package com.agentops.identity.security;

import com.agentops.shared.api.ApiResponse;
import com.agentops.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        ErrorCode errorCode = resolveErrorCode(exception);
        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(errorCode.code(), errorCode.defaultMessage())
        );
    }

    private ErrorCode resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof JwtAuthenticationException jwtException) {
            return jwtException.reason() == JwtAuthenticationException.Reason.EXPIRED
                    ? ErrorCode.TOKEN_EXPIRED
                    : ErrorCode.INVALID_TOKEN;
        }
        return ErrorCode.UNAUTHORIZED;
    }
}
