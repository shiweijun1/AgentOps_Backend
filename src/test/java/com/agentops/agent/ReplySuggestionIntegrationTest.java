package com.agentops.agent;

import com.agentops.agent.application.*;
import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.*;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.knowledge.application.KnowledgeApplicationService;
import com.agentops.shared.exception.BusinessException;
import com.agentops.ticket.api.CreateTicketRequest;
import com.agentops.ticket.application.TicketApplicationService;
import com.agentops.ticket.domain.TicketPriority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.dev_seed_enabled=true",
        "spring.flyway.placeholders.dev_admin_password_hash=$2a$12$4RjCpvCT7V6ZKG/cEWsbq.UB.s/hHLNDypgrN3CjCd1sQ.yZl1o2S",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "agentops.messaging.enabled=false", "agentops.agent.worker-enabled=false", "agentops.ai.provider=fake"
})
class ReplySuggestionIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("agentops");
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private final AgentOpsPrincipal customer = new AgentOpsPrincipal(CUSTOMER, "default", "customer", "Customer",
            null, null, Set.of("CUSTOMER"), Set.of("ticket:create"));
    private final AgentOpsPrincipal admin = new AgentOpsPrincipal(ADMIN, "default", "admin", "Admin",
            null, null, Set.of("ADMIN"), Set.of("ticket:read:any", "suggestion:read", "suggestion:review"));

    @Autowired TicketApplicationService tickets;
    @Autowired KnowledgeApplicationService knowledge;
    @Autowired AgentRunCreationService creation;
    @Autowired AgentWorker analysisWorker;
    @Autowired ReplySuggestionWorker replyWorker;
    @Autowired AgentRunRepository runs;
    @Autowired AiSuggestionRepository suggestions;
    @Autowired KnowledgeCitationRepository citations;
    @Autowired SuggestionReviewService review;

    @Test void generatedSuggestionKeepsSourcesAndManualSnapshots() {
        var article = knowledge.createArticle("default", "星河账号指南", null, null);
        var version = knowledge.createVersion(article.getId(), "default",
                "星河账号登录失败时，请先检查账号状态与双重认证。", "reviewer");
        knowledge.publish(article.getId(), version.getId(), "default", "reviewer");
        var ticket = tickets.create(new CreateTicketRequest("星河账号登录", "星河账号无法登录，请帮忙检查。",
                TicketPriority.MEDIUM), UUID.randomUUID().toString(), customer);
        creation.manual(ticket.getId(), "default");
        analysisWorker.runBatch();
        AgentRun analysis = runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(
                ticket.getId(), AgentRunType.TICKET_ANALYSIS, 1, 1).orElseThrow();
        assertThat(creation.fromSuccessfulAnalysis(analysis).getId())
                .isEqualTo(creation.fromSuccessfulAnalysis(analysis).getId());
        AgentRun reply = runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(
                ticket.getId(), AgentRunType.REPLY_SUGGESTION, 1, 1).orElseThrow();
        assertThat(reply.getStatus()).isEqualTo(AgentRunStatus.PENDING);
        replyWorker.runBatch();
        assertThat(runs.findById(reply.getId()).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED);
        AiSuggestion suggestion = suggestions.findByRunId(reply.getId()).orElseThrow();
        var source = citations.findByTenantIdAndSuggestionIdOrderByRankNo("default", suggestion.getId()).getFirst();
        assertThat(source.isUsedInAnswer()).isTrue();
        assertThat(source.getKnowledgeVersionId()).isEqualTo(version.getId());
        assertThat(suggestion.getOriginalContent()).contains("[citation:" + source.getChunkId() + "]");
        String edit = "请核查账号状态。[citation:" + source.getChunkId() + "]";
        var edited = review.edit(suggestion.getId(), suggestion.getVersion(), edit, admin);
        assertThat(edited.originalContent()).isEqualTo(suggestion.getOriginalContent());
        assertThat(edited.editedContent()).isEqualTo(edit);
        assertThatThrownBy(() -> review.adopt(suggestion.getId(), 0, admin))
                .isInstanceOf(BusinessException.class);
        var adopted = review.adopt(suggestion.getId(), edited.version(), admin);
        assertThat(adopted.status()).isEqualTo(AiSuggestionStatus.ADOPTED);
        assertThat(adopted.finalContentSnapshot()).isEqualTo(edit);
        assertThatThrownBy(() -> review.reject(suggestion.getId(), adopted.version(), "no", admin))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> review.get(suggestion.getId(), new AgentOpsPrincipal(ADMIN, "other",
                "other", "Other", null, null, Set.of("ADMIN"), Set.of("ticket:read:any"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test void noKnowledgeMatchFailsClosedWithoutSuggestion() {
        var ticket = tickets.create(new CreateTicketRequest("火星故障", "无法解决的未知问题。",
                TicketPriority.MEDIUM), UUID.randomUUID().toString(), customer);
        creation.manual(ticket.getId(), "default");
        analysisWorker.runBatch();
        AgentRun reply = runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(
                ticket.getId(), AgentRunType.REPLY_SUGGESTION, 1, 1).orElseThrow();
        replyWorker.runBatch();
        assertThat(runs.findById(reply.getId()).orElseThrow().getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(suggestions.findByRunId(reply.getId())).isEmpty();
    }

    @Test void withdrawnCitationCannotBeAdopted() {
        var article = knowledge.createArticle("default", "北斗账号指南", null, Instant.now().plusSeconds(3600));
        var version = knowledge.createVersion(article.getId(), "default", "北斗账号登录失败时请检查服务状态。", "reviewer");
        knowledge.publish(article.getId(), version.getId(), "default", "reviewer");
        assertThat(knowledge.search("default", "北斗", 10))
                .extracting("articleId").contains(article.getId());
        var ticket = tickets.create(new CreateTicketRequest("北斗账号登录", "北斗账号登录失败", TicketPriority.MEDIUM),
                UUID.randomUUID().toString(), customer);
        creation.manual(ticket.getId(), "default");
        analysisWorker.runBatch(); replyWorker.runBatch();
        AgentRun reply = runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(
                ticket.getId(), AgentRunType.REPLY_SUGGESTION, 1, 1).orElseThrow();
        AiSuggestion suggestion = suggestions.findByRunId(reply.getId()).orElseThrow();
        var cited = citations.findByTenantIdAndSuggestionIdOrderByRankNo("default", suggestion.getId())
                .stream().filter(KnowledgeCitation::isUsedInAnswer).findFirst().orElseThrow();
        knowledge.withdraw(cited.getArticleId(), "default");
        assertThatThrownBy(() -> review.adopt(suggestion.getId(), suggestion.getVersion(), admin))
                .isInstanceOf(BusinessException.class);
        assertThat(suggestions.findById(suggestion.getId()).orElseThrow().getStatus())
                .isEqualTo(AiSuggestionStatus.READY);
    }
}
