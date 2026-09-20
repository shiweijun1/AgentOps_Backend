package com.agentops.identity.api;

import com.agentops.identity.application.AuthenticatedUserLoader;
import com.agentops.identity.application.AuthenticationService;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.identity.security.JwtAuthenticationFilter;
import com.agentops.identity.security.JwtProperties;
import com.agentops.identity.security.JwtService;
import com.agentops.identity.security.RestAccessDeniedHandler;
import com.agentops.identity.security.RestAuthenticationEntryPoint;
import com.agentops.identity.security.SecurityConfiguration;
import com.agentops.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {AuthController.class, AdminSecurityController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class
)
@ActiveProfiles("test")
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtService.class,
        GlobalExceptionHandler.class,
        AuthApiSecurityTest.JwtTestConfiguration.class
})
class AuthApiSecurityTest {

    private static final String SECRET = "test-secret-that-is-longer-than-thirty-two-bytes-123456";
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private AuthenticatedUserLoader authenticatedUserLoader;

    @Test
    void loginShouldReturnAccessToken() throws Exception {
        when(authenticationService.login(any())).thenReturn(new LoginResponse(
                "Bearer",
                "header.payload.signature",
                900,
                Instant.parse("2030-01-01T00:15:00Z")
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "default",
                                  "username": "admin",
                                  "password": "Admin@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("header.payload.signature"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void loginFailureShouldReturnUnauthorized() throws Exception {
        when(authenticationService.login(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException(
                        "用户名或密码错误"
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @Test
    void meWithoutTokenShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @Test
    void meWithValidTokenShouldReturnCurrentUser() throws Exception {
        AgentOpsPrincipal principal = principal(Set.of("identity:me:read"));
        String token = jwtService.issueAccessToken(principal).value();
        when(authenticatedUserLoader.load(eq(USER_ID), eq("default"))).thenReturn(principal);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("viewer"))
                .andExpect(jsonPath("$.data.roles[0]").value("REQUESTER"));
    }

    @Test
    void adminCheckWithoutPermissionShouldReturnForbidden() throws Exception {
        AgentOpsPrincipal principal = principal(Set.of("identity:me:read"));
        String token = jwtService.issueAccessToken(principal).value();
        when(authenticatedUserLoader.load(eq(USER_ID), eq("default"))).thenReturn(principal);

        mockMvc.perform(get("/api/v1/admin/security-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void adminCheckWithPermissionShouldSucceed() throws Exception {
        AgentOpsPrincipal principal = principal(Set.of("admin:security-check"));
        String token = jwtService.issueAccessToken(principal).value();
        when(authenticatedUserLoader.load(eq(USER_ID), eq("default"))).thenReturn(principal);

        mockMvc.perform(get("/api/v1/admin/security-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permission").value("admin:security-check"));
    }

    @Test
    void expiredTokenShouldReturnExplicitError() throws Exception {
        JwtProperties properties = new JwtProperties(
                "agentops-test",
                SECRET,
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );
        JwtService oldIssuer = new JwtService(
                properties,
                Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC)
        );
        String expiredToken = oldIssuer.issueAccessToken(principal(Set.of())).value();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    private AgentOpsPrincipal principal(Set<String> permissions) {
        return new AgentOpsPrincipal(
                USER_ID,
                "default",
                "viewer",
                "Viewer",
                "viewer@agentops.local",
                null,
                Set.of("REQUESTER"),
                permissions
        );
    }

    @TestConfiguration
    static class JwtTestConfiguration {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "agentops-test",
                    SECRET,
                    Duration.ofMinutes(15),
                    Duration.ofDays(7)
            );
        }
    }
}
