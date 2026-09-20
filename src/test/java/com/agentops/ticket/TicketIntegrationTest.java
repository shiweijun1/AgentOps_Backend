package com.agentops.ticket;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.api.AssignTicketRequest;
import com.agentops.ticket.api.CreateTicketRequest;
import com.agentops.ticket.application.TicketApplicationService;
import com.agentops.ticket.application.TicketSearchCriteria;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;
import com.agentops.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.dev_seed_enabled=true",
        "spring.flyway.placeholders.dev_admin_password_hash=$2a$12$4RjCpvCT7V6ZKG/cEWsbq.UB.s/hHLNDypgrN3CjCd1sQ.yZl1o2S",
        "agentops.messaging.enabled=false",
        "agentops.agent.worker-enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class TicketIntegrationTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID SUPPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("agentops");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired TicketApplicationService ticketService;
    @Autowired TicketRepository ticketRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void shouldCreateReplayFilterProtectAndAssignTicket() {
        String key = "integration-create-" + UUID.randomUUID();
        CreateTicketRequest request = new CreateTicketRequest("Payment failed", "Card was rejected", null);

        Ticket created = ticketService.create(request, key, customer());
        Ticket replay = ticketService.create(request, key, customer());

        assertThat(replay.getId()).isEqualTo(created.getId());
        assertThat(ticketService.search(
                new TicketSearchCriteria(TicketStatus.NEW, null, null, null, null), 0, 20, admin()
        ).getContent()).extracting(Ticket::getId).contains(created.getId());

        Ticket adminTicket = ticketService.create(
                new CreateTicketRequest("Internal", "Admin-created ticket", TicketPriority.HIGH),
                "integration-admin-" + UUID.randomUUID(), admin()
        );
        assertThatThrownBy(() -> ticketService.get(adminTicket.getId(), customer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        Ticket assigned = ticketService.assign(
                created.getId(), new AssignTicketRequest(TEAM_ID, SUPPORT_ID, "route to support", created.getVersion()),
                admin()
        );
        assertThat(assigned.getAssigneeId()).isEqualTo(SUPPORT_ID);
        assertThat(ticketService.assignments(created.getId(), admin())).hasSize(1);
        assertThat(ticketService.transitions(created.getId(), admin()))
                .extracting(record -> record.getToStatus()).containsExactly(TicketStatus.NEW);
    }

    @Test
    void concurrentUpdatesShouldCauseOptimisticLockConflict() throws Exception {
        Ticket created = ticketService.create(
                new CreateTicketRequest("Concurrency", "Test optimistic locking", null),
                "integration-lock-" + UUID.randomUUID(), customer()
        );
        CountDownLatch loaded = new CountDownLatch(2);
        CountDownLatch update = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentAssignment(
                    transactions, created.getId(), TEAM_ID, SUPPORT_ID, loaded, update));
            var second = executor.submit(() -> concurrentAssignment(
                    transactions, created.getId(), null, ADMIN_ID, loaded, update));

            loaded.await();
            update.countDown();
            Throwable firstFailure = first.get();
            Throwable secondFailure = second.get();

            assertThat((firstFailure == null) ^ (secondFailure == null)).isTrue();
            Throwable conflict = firstFailure == null ? secondFailure : firstFailure;
            assertThat(hasOptimisticLockCause(conflict)).isTrue();
        }
    }

    private Throwable concurrentAssignment(
            TransactionTemplate transactions,
            UUID ticketId,
            UUID teamId,
            UUID assigneeId,
            CountDownLatch loaded,
            CountDownLatch update
    ) {
        try {
            transactions.executeWithoutResult(status -> {
                Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
                loaded.countDown();
                await(update);
                ticket.assign(teamId, assigneeId);
                ticketRepository.flush();
            });
            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private boolean hasOptimisticLockCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private AgentOpsPrincipal customer() {
        return new AgentOpsPrincipal(
                CUSTOMER_ID, "default", "customer", "Customer", null, null,
                Set.of("CUSTOMER"), Set.of("ticket:create", "ticket:read:self")
        );
    }

    private AgentOpsPrincipal admin() {
        return new AgentOpsPrincipal(
                ADMIN_ID, "default", "admin", "Admin", null, null,
                Set.of("ADMIN"), Set.of("ticket:create", "ticket:read:any", "ticket:assign", "ticket:transition")
        );
    }
}
