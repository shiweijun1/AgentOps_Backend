package com.agentops.identity.security;

import com.agentops.identity.application.AuthenticatedUserLoader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthenticatedUserLoader userLoader;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AuthenticatedUserLoader userLoader,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtService = jwtService;
        this.userLoader = userLoader;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().equals("/api/v1/auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)
                || !StringUtils.hasText(authorization.substring(BEARER_PREFIX.length()))) {
            authenticationEntryPoint.commence(
                    request,
                    response,
                    JwtAuthenticationException.invalid(null)
            );
            return;
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            JwtService.JwtIdentity identity = jwtService.parseAccessToken(token);
            AgentOpsPrincipal principal = userLoader.load(identity.userId(), identity.tenantId());

            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    principal.authorities()
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            log.debug("JWT authentication rejected: {}", exception.getClass().getSimpleName());
            AuthenticationException responseException = exception instanceof JwtAuthenticationException
                    ? exception
                    : JwtAuthenticationException.invalid(exception);
            authenticationEntryPoint.commence(request, response, responseException);
        }
    }
}
