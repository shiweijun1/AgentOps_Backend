package com.agentops.agent.api;

import com.agentops.agent.application.SuggestionReviewService;
import com.agentops.identity.application.AuthenticatedUserLoader;
import com.agentops.identity.security.*;
import com.agentops.shared.exception.GlobalExceptionHandler;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SuggestionController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class, GlobalExceptionHandler.class,
        SuggestionApiSecurityTest.JwtTestConfiguration.class})
class SuggestionApiSecurityTest {
    private static final UUID USER = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean SuggestionReviewService service;
    @MockitoBean AuthenticatedUserLoader loader;

    @Test void anonymousCannotReadSuggestion() throws Exception {
        mvc.perform(get("/api/v1/suggestions/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test void customerWithoutPermissionCannotReview() throws Exception {
        AgentOpsPrincipal principal = new AgentOpsPrincipal(USER, "default", "customer", "Customer",
                null, null, Set.of("CUSTOMER"), Set.of("ticket:read:self"));
        when(loader.load(USER, "default")).thenReturn(principal);
        mvc.perform(post("/api/v1/suggestions/{id}/adopt", UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwt.issueAccessToken(principal).value())
                        .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean JwtProperties jwtProperties() {
            return new JwtProperties("agentops-test", "test-secret-that-is-longer-than-thirty-two-bytes-123456",
                    Duration.ofMinutes(15), Duration.ofDays(7));
        }
    }
}
