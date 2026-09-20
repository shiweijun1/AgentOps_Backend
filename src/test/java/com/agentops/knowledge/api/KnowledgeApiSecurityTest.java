package com.agentops.knowledge.api;

import com.agentops.identity.application.AuthenticatedUserLoader;
import com.agentops.identity.security.*;
import com.agentops.knowledge.application.KnowledgeApplicationService;
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

@WebMvcTest(controllers = KnowledgeController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class, GlobalExceptionHandler.class,
        KnowledgeApiSecurityTest.JwtTestConfiguration.class})
class KnowledgeApiSecurityTest {
    private static final UUID USER_ID = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean KnowledgeApplicationService service;
    @MockitoBean AuthenticatedUserLoader loader;

    @Test void missingTokenCannotSearch() throws Exception {
        mvc.perform(get("/api/v1/knowledge/search").param("q", "登录"))
                .andExpect(status().isUnauthorized());
    }

    @Test void customerWithoutPermissionCannotSearchOrManage() throws Exception {
        AgentOpsPrincipal principal = new AgentOpsPrincipal(USER_ID, "default", "customer", "Customer",
                null, null, Set.of("CUSTOMER"), Set.of("ticket:read:self"));
        when(loader.load(USER_ID, "default")).thenReturn(principal);
        String token = jwt.issueAccessToken(principal).value();
        mvc.perform(get("/api/v1/knowledge/search").param("q", "登录")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_403"));
        mvc.perform(post("/api/v1/knowledge/articles").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"title\":\"Demo\"}"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean JwtProperties jwtProperties() {
            return new JwtProperties("agentops-test", "test-secret-that-is-longer-than-thirty-two-bytes-123456",
                    Duration.ofMinutes(15), Duration.ofDays(7));
        }
    }
}
