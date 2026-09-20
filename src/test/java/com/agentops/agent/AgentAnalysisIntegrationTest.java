package com.agentops.agent;

import com.agentops.agent.application.*;
import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.*;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.infrastructure.messaging.MessagingProperties;
import com.agentops.infrastructure.messaging.inbox.InboxMessageRepository;
import com.agentops.infrastructure.messaging.inbox.InboxStatus;
import com.agentops.infrastructure.messaging.outbox.*;
import com.agentops.ticket.api.CreateTicketRequest;
import com.agentops.ticket.application.TicketApplicationService;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.event.TicketCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.dev_seed_enabled=true",
        "spring.flyway.placeholders.dev_admin_password_hash=$2a$12$4RjCpvCT7V6ZKG/cEWsbq.UB.s/hHLNDypgrN3CjCd1sQ.yZl1o2S",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "agentops.messaging.enabled=true",
        "agentops.messaging.outbox.scheduling-enabled=false",
        "agentops.agent.worker-enabled=false",
        "agentops.ai.provider=fake"
})
class AgentAnalysisIntegrationTest {
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    @Container static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("agentops");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management-alpine"));

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired TicketApplicationService tickets;
    @Autowired OutboxEventRepository outbox;
    @Autowired OutboxPublisher publisher;
    @Autowired EventBroker broker;
    @Autowired ObjectMapper mapper;
    @Autowired MessagingProperties messaging;
    @Autowired InboxMessageRepository inbox;
    @Autowired AgentRunRepository runs;
    @Autowired AgentStepRepository steps;
    @Autowired AiAnalysisResultRepository results;
    @Autowired AgentRunCreationService creation;
    @Autowired AgentRunClaimService claims;
    @Autowired AgentWorker worker;
    @Autowired JdbcTemplate jdbc;

    @Test void rabbitEventCreatesOneRunThenWorkerPersistsAnalysisAndManualRerun() throws Exception {
        Ticket ticket = createTicket();
        OutboxEvent event = outbox.findByAggregateId(ticket.getId().toString()).orElseThrow();
        TicketCreatedEvent payload = mapper.readValue(event.getPayload(), TicketCreatedEvent.class);

        publisher.publishBatch();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(inbox.findByEventIdAndConsumerName(payload.eventId(), messaging.getConsumerName())
                    .orElseThrow().getStatus()).isEqualTo(InboxStatus.PROCESSED);
            assertThat(runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(ticket.getId(),
                    AgentRunType.TICKET_ANALYSIS, 1, 1).orElseThrow().getStatus())
                    .isEqualTo(AgentRunStatus.PENDING);
        });
        broker.publish(new OutboxDispatch(event.getId(), event.getEventId(), event.getEventType(),
                event.getAggregateId(), payload.traceId(), event.getPayload()));
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(runs.findByTenantIdAndTicketIdOrderByCreatedAtDesc("default", ticket.getId())).hasSize(1));

        worker.runBatch();
        AgentRun run = runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(ticket.getId(),
                AgentRunType.TICKET_ANALYSIS, 1, 1).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(steps.findByRunIdOrderBySequenceNo(run.getId())).hasSize(4);
        assertThat(results.findByRunId(run.getId())).isPresent();

        AgentRun rerun = creation.manual(ticket.getId(), "default");
        assertThat(rerun.getAttemptNo()).isEqualTo(2);
        assertThat(rerun.getParentRunId()).isEqualTo(run.getId());
    }

    @Test void leasePreventsSecondWorkerAndExpiredLeaseCanBeReclaimed() {
        Ticket ticket = createTicket();
        AgentRun run = creation.manual(ticket.getId(), "default");
        AgentRunClaimService.Claim first = claims.claim().stream()
                .filter(claim -> claim.runId().equals(run.getId())).findFirst().orElseThrow();
        assertThat(claims.claim()).noneMatch(claim -> claim.runId().equals(run.getId()));
        jdbc.update("UPDATE agent_run SET lease_until = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND " +
                "WHERE id = UUID_TO_BIN(?)", run.getId().toString());
        AgentRunClaimService.Claim takeover = claims.claim().stream()
                .filter(claim -> claim.runId().equals(run.getId())).findFirst().orElseThrow();
        assertThat(takeover.owner()).isNotEqualTo(first.owner());
        assertThat(takeover.executionCount()).isEqualTo(2);
    }

    private Ticket createTicket() {
        AgentOpsPrincipal customer = new AgentOpsPrincipal(CUSTOMER_ID, "default", "customer", "Customer",
                null, null, Set.of("CUSTOMER"), Set.of("ticket:create", "ticket:read:self"));
        return tickets.create(new CreateTicketRequest("Cannot access account", "Login fails repeatedly",
                TicketPriority.MEDIUM), UUID.randomUUID().toString(), customer);
    }
}
