package com.agentops.ticket.api;

import com.agentops.identity.application.AuthenticatedUserLoader;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.identity.security.JwtAuthenticationFilter;
import com.agentops.identity.security.JwtProperties;
import com.agentops.identity.security.JwtService;
import com.agentops.identity.security.RestAccessDeniedHandler;
import com.agentops.identity.security.RestAuthenticationEntryPoint;
import com.agentops.identity.security.SecurityConfiguration;
import com.agentops.shared.exception.GlobalExceptionHandler;
import com.agentops.ticket.application.TicketApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TicketController.class,
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
        TicketApiSecurityTest.JwtTestConfiguration.class
})
class TicketApiSecurityTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockitoBean TicketApplicationService ticketService;
    @MockitoBean AuthenticatedUserLoader authenticatedUserLoader;

    @Test
    void unauthenticatedRequestShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @Test
    void authenticatedUserWithoutTicketPermissionShouldReturnForbidden() throws Exception {
        AgentOpsPrincipal principal = new AgentOpsPrincipal(
                USER_ID, "default", "viewer", "Viewer", null, null,
                Set.of("VIEWER"), Set.of("identity:me:read")
        );
        String token = jwtService.issueAccessToken(principal).value();
        when(authenticatedUserLoader.load(eq(USER_ID), eq("default"))).thenReturn(principal);

        mockMvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "agentops-test",
                    "test-secret-that-is-longer-than-thirty-two-bytes-123456",
                    Duration.ofMinutes(15),
                    Duration.ofDays(7)
            );
        }
    }
}
