package com.agentops.infrastructure.messaging;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.infrastructure.messaging.inbox.InboxMessageRepository;
import com.agentops.infrastructure.messaging.inbox.InboxStatus;
import com.agentops.infrastructure.messaging.outbox.EventBroker;
import com.agentops.infrastructure.messaging.outbox.OutboxDispatch;
import com.agentops.infrastructure.messaging.outbox.OutboxClaimService;
import com.agentops.infrastructure.messaging.outbox.OutboxEvent;
import com.agentops.infrastructure.messaging.outbox.OutboxEventRepository;
import com.agentops.infrastructure.messaging.outbox.OutboxPublisher;
import com.agentops.infrastructure.messaging.outbox.OutboxStatus;
import com.agentops.ticket.api.CreateTicketRequest;
import com.agentops.ticket.application.TicketApplicationService;
import com.agentops.ticket.application.TicketCreatedEventHandler;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.event.TicketCreatedEvent;
import com.agentops.ticket.infrastructure.persistence.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
        "agentops.messaging.consumer.max-retries=2"
})
class ReliableMessagingIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("agentops");

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management-alpine")
    );

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired TicketApplicationService ticketService;
    @Autowired TicketRepository ticketRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired InboxMessageRepository inboxRepository;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired OutboxClaimService outboxClaimService;
    @Autowired EventBroker eventBroker;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired MessagingProperties messagingProperties;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean TicketCreatedEventHandler eventHandler;

    @Test
    void ticketAndOutboxShouldCommitAndRollbackTogether() {
        Ticket committed = createTicket("outbox-commit", "Committed ticket");
        assertThat(outboxRepository.findByAggregateId(committed.getId().toString()))
                .get().extracting(OutboxEvent::getStatus).isEqualTo(OutboxStatus.PENDING);

        long ticketsBefore = ticketRepository.count();
        long outboxBefore = outboxRepository.count();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            createTicket("outbox-rollback", "Rolled back ticket");
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ticketRepository.count()).isEqualTo(ticketsBefore);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void publisherLeaseShouldPreventAnotherWorkerFromClaimingTheSameEvent() {
        Ticket ticket = createTicket("outbox-lease", "Publisher lease");
        OutboxEvent outbox = outboxRepository.findByAggregateId(ticket.getId().toString()).orElseThrow();

        var firstClaim = outboxClaimService.claim("worker-a");
        var secondClaim = outboxClaimService.claim("worker-b");

        assertThat(firstClaim).extracting(OutboxDispatch::eventId).contains(outbox.getEventId());
        assertThat(secondClaim).extracting(OutboxDispatch::eventId).doesNotContain(outbox.getEventId());
    }

    @Test
    void shouldPublishConsumeAndDeduplicateRedelivery() throws Exception {
        Ticket ticket = createTicket("outbox-success", "Async success");
        OutboxEvent outbox = outboxRepository.findByAggregateId(ticket.getId().toString()).orElseThrow();
        TicketCreatedEvent event = objectMapper.readValue(outbox.getPayload(), TicketCreatedEvent.class);

        outboxPublisher.publishBatch();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(outboxRepository.findById(outbox.getId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(inboxRepository.findByEventIdAndConsumerName(
                    event.eventId(), messagingProperties.getConsumerName()).orElseThrow().getStatus())
                    .isEqualTo(InboxStatus.PROCESSED);
        });
        verify(eventHandler, times(1)).handle(event);

        eventBroker.publish(dispatch(outbox, event));

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(eventHandler, times(1)).handle(event));
        assertThat(inboxRepository.findByEventIdAndConsumerName(
                event.eventId(), messagingProperties.getConsumerName()).orElseThrow().getStatus())
                .isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void consumerFailureShouldRemainFailedAndReachDeadLetterQueue() throws Exception {
        Ticket ticket = createTicket("outbox-failure", "Async failure");
        OutboxEvent outbox = outboxRepository.findByAggregateId(ticket.getId().toString()).orElseThrow();
        TicketCreatedEvent event = objectMapper.readValue(outbox.getPayload(), TicketCreatedEvent.class);
        doThrow(new RuntimeException("retryable failure")).when(eventHandler).handle(eq(event));

        outboxPublisher.publishBatch();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var inbox = inboxRepository.findByEventIdAndConsumerName(
                    event.eventId(), messagingProperties.getConsumerName()).orElseThrow();
            assertThat(inbox.getStatus()).isEqualTo(InboxStatus.FAILED);
            assertThat(inbox.getRetryCount()).isEqualTo(2);
        });
        assertThat(rabbitTemplate.receive(messagingProperties.getDeadLetterQueue(), 5000)).isNotNull();
        verify(eventHandler, times(2)).handle(event);
    }

    private Ticket createTicket(String keyPrefix, String title) {
        return ticketService.create(
                new CreateTicketRequest(title, "Message delivery integration test", TicketPriority.MEDIUM),
                keyPrefix + "-" + UUID.randomUUID(),
                customer()
        );
    }

    private OutboxDispatch dispatch(OutboxEvent outbox, TicketCreatedEvent event) {
        return new OutboxDispatch(
                outbox.getId(), outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId(),
                event.traceId(), outbox.getPayload()
        );
    }

    private AgentOpsPrincipal customer() {
        return new AgentOpsPrincipal(
                CUSTOMER_ID, "default", "customer", "Customer", null, null,
                Set.of("CUSTOMER"), Set.of("ticket:create", "ticket:read:self")
        );
    }
}
