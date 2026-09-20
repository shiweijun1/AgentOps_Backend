package com.agentops.analytics.api;

import com.agentops.analytics.application.AnalyticsOverviewService;
import com.agentops.identity.application.AuthenticatedUserLoader;
import com.agentops.identity.security.*;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class, GlobalExceptionHandler.class,
        AnalyticsApiSecurityTest.JwtTestConfiguration.class})
class AnalyticsApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean AuthenticatedUserLoader loader;
    @MockitoBean AnalyticsOverviewService service;

    @Test void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void supportCannotAccessEvenWithOtherPermissions() throws Exception {
        var support = principal("SUPPORT");
        when(loader.load(support.userId(), support.tenantId())).thenReturn(support);
        mvc.perform(get("/api/v1/analytics/overview")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-01-02T00:00:00Z")
                        .header("Authorization", "Bearer " + jwt.issueAccessToken(support).value()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_403"));
        verifyNoInteractions(service);
    }

    @Test void adminReachesApplicationService() throws Exception {
        var admin = principal("ADMIN");
        when(loader.load(admin.userId(), admin.tenantId())).thenReturn(admin);
        mvc.perform(get("/api/v1/analytics/overview")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-01-02T00:00:00Z")
                        .header("Authorization", "Bearer " + jwt.issueAccessToken(admin).value()))
                .andExpect(status().isOk());
    }

    @Test void adminWithMissingRangeGetsBadRequest() throws Exception {
        var admin = principal("ADMIN");
        when(loader.load(admin.userId(), admin.tenantId())).thenReturn(admin);
        when(service.overview(null, null, admin)).thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));
        mvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + jwt.issueAccessToken(admin).value()))
                .andExpect(status().isBadRequest());
    }

    private static AgentOpsPrincipal principal(String role) {
        return new AgentOpsPrincipal(UUID.randomUUID(), "tenant-a", "user", "User", null,
                null, Set.of(role), Set.of("ticket:read:any"));
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean JwtProperties jwtProperties() {
            return new JwtProperties("agentops-test", "test-secret-that-is-longer-than-thirty-two-bytes-123456",
                    Duration.ofMinutes(15), Duration.ofDays(7));
        }
    }
}
