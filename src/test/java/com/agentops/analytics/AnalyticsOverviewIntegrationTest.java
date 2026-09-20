package com.agentops.analytics;

import com.agentops.analytics.application.AnalyticsOverviewService;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.ticket.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.dev_seed_enabled=true",
        "spring.flyway.placeholders.dev_admin_password_hash=$2a$12$4RjCpvCT7V6ZKG/cEWsbq.UB.s/hHLNDypgrN3CjCd1sQ.yZl1o2S",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "agentops.messaging.enabled=false",
        "agentops.agent.worker-enabled=false"
})
class AnalyticsOverviewIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("agentops");

    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired AnalyticsOverviewService service;
    @Autowired JdbcTemplate jdbc;

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-02T00:00:00Z");
    private static final UUID SEEDED_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final AtomicInteger ATTEMPT_SEQUENCE = new AtomicInteger();

    @Test void emptyAndZeroDenominatorAreExplicit() {
        var result = service.overview(FROM.toString(), TO.toString(), admin("empty-tenant"));
        assertThat(result.tickets().createdCount()).isZero();
        assertThat(result.tickets().currentStatusDistribution()).containsValues(0L).hasSize(6);
        assertThat(result.tickets().averageFirstResponseMillis()).isNull();
        assertThat(result.tickets().resolutionRate()).isEqualByComparingTo("0");
        assertThat(result.agentRuns().successRate()).isEqualByComparingTo("0");
        assertThat(result.suggestions().adoptionRate()).isEqualByComparingTo("0");
        assertThat(result.suggestions().adoptedSendRate()).isEqualByComparingTo("0");
    }

    @Test void cohortMetricsUseCorrectTimeFieldsAndExcludeUpperBoundary() {
        String tenant = "analytics-cohort";
        UUID first = ticket(tenant, FROM, "RESOLVED", FROM.plusSeconds(600));
        UUID second = ticket(tenant, FROM.plusSeconds(10), "CLOSED", FROM.plusSeconds(1210));
        ticket(tenant, TO.minusNanos(1000), "NEW", null);
        ticket(tenant, TO, "RESOLVED", TO.plusSeconds(30));
        ticket(tenant, FROM.minusNanos(1000), "CLOSED", null);

        UUID successRun = run(tenant, first, "SUCCEEDED", FROM);
        run(tenant, second, "FAILED", TO.minusNanos(1000));
        run(tenant, first, "SUCCEEDED", TO);
        run(tenant, second, "PENDING", null);

        UUID adopted = suggestion(tenant, first, successRun, "ADOPTED", FROM);
        suggestion(tenant, first, run(tenant, first, "SUCCEEDED", FROM.plusSeconds(5)), "READY", FROM.plusSeconds(5));
        suggestion(tenant, second, run(tenant, second, "SUCCEEDED", TO), "ADOPTED", TO);
        sentMessage(tenant, first, adopted, FROM.plusSeconds(20));

        var result = service.overview("2026-03-01T08:00:00+08:00", TO.toString(), admin(tenant));
        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.tickets().createdCount()).isEqualTo(3);
        assertThat(result.tickets().currentStatusDistribution()).containsEntry(TicketStatus.RESOLVED, 1L)
                .containsEntry(TicketStatus.CLOSED, 1L).containsEntry(TicketStatus.NEW, 1L);
        assertThat(result.tickets().firstRespondedCount()).isEqualTo(2);
        assertThat(result.tickets().averageFirstResponseMillis()).isEqualByComparingTo("900000");
        assertThat(result.tickets().resolvedOrClosedCount()).isEqualTo(2);
        assertThat(result.tickets().resolutionRate()).isEqualByComparingTo("0.6667");
        assertThat(result.agentRuns().completedCount()).isEqualTo(3);
        assertThat(result.agentRuns().succeededCount()).isEqualTo(2);
        assertThat(result.agentRuns().successRate()).isEqualByComparingTo("0.6667");
        assertThat(result.suggestions().generatedCount()).isEqualTo(2);
        assertThat(result.suggestions().adoptedCount()).isEqualTo(1);
        assertThat(result.suggestions().adoptionRate()).isEqualByComparingTo("0.5000");
        assertThat(result.suggestions().sentAdoptedCount()).isEqualTo(1);
        assertThat(result.suggestions().adoptedSendRate()).isEqualByComparingTo("1.0000");
    }

    @Test void tenantIsolationIncludesAllSixMeasures() {
        String other = "analytics-other";
        UUID ticket = ticket(other, FROM, "CLOSED", FROM.plusSeconds(60));
        UUID run = run(other, ticket, "SUCCEEDED", FROM.plusSeconds(70));
        UUID adopted = suggestion(other, ticket, run, "ADOPTED", FROM);
        sentMessage(other, ticket, adopted, FROM.plusSeconds(80));
        var result = service.overview(FROM.toString(), TO.toString(), admin("analytics-isolated"));
        assertThat(result.tickets().createdCount()).isZero();
        assertThat(result.agentRuns().completedCount()).isZero();
        assertThat(result.suggestions().generatedCount()).isZero();
    }

    @Test void invalidRangeAndNonAdminAreRejected() {
        var admin = admin("analytics-validation");
        assertThatThrownBy(() -> service.overview(FROM.toString(), FROM.toString(), admin))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.overview(FROM.toString(), FROM.plusSeconds(91L * 86400).toString(), admin))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.overview("2026-03-01T00:00:00", TO.toString(), admin))
                .isInstanceOf(BusinessException.class);
        var support = new AgentOpsPrincipal(UUID.randomUUID(), "analytics-validation", "support", "Support",
                null, null, Set.of("SUPPORT"), Set.of());
        assertThatThrownBy(() -> service.overview(FROM.toString(), TO.toString(), support))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private UUID ticket(String tenant, Instant created, String status, Instant firstResponse) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket (id, tenant_id, ticket_no, requester_id, title, description, status,
                    priority, routing_type, content_revision, content_fingerprint, reopen_count, first_response_at,
                    submitted_at, version, created_at, updated_at, created_by, updated_by)
                VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), 'analytics fixture', 'fixture', ?,
                    'MEDIUM', 'MANUAL', 1, REPEAT('a',64), 0, ?, ?, 0, ?, ?, 'test', 'test')
                """, id.toString(), tenant, id.toString().substring(0, 32), SEEDED_ADMIN.toString(), status,
                utc(firstResponse), utc(created), utc(created), utc(created));
        return id;
    }

    private UUID run(String tenant, UUID ticket, String status, Instant finished) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO agent_run (id, tenant_id, ticket_id, run_type, input_revision, attempt_no, status,
                    trigger_type, input_tokens, output_tokens, estimated_cost, version, created_at, updated_at, finished_at)
                VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), 'TICKET_ANALYSIS', 1, ?,
                    ?, 'MANUAL', 0, 0, 0, 0, ?, ?, ?)
                """, id.toString(), tenant, ticket.toString(), ATTEMPT_SEQUENCE.incrementAndGet(),
                status, utc(FROM), utc(FROM), utc(finished));
        return id;
    }

    private UUID suggestion(String tenant, UUID ticket, UUID run, String status, Instant created) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_suggestion (id, tenant_id, ticket_id, run_id, input_revision, original_content,
                    status, confidence, risk_level, adopted_by, adopted_at, final_content_snapshot, version, created_at)
                VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), UUID_TO_BIN(?), 1, 'fixture',
                    ?, 0.9, 'LOW', UUID_TO_BIN(?), ?, 'fixture', 0, ?)
                """, id.toString(), tenant, ticket.toString(), run.toString(), status, SEEDED_ADMIN.toString(),
                "ADOPTED".equals(status) ? utc(created) : null, utc(created));
        return id;
    }

    private void sentMessage(String tenant, UUID ticket, UUID suggestion, Instant created) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_message (id, tenant_id, ticket_id, sender_type, sender_id, message_type,
                    content, source_suggestion_id, client_request_id, visible_to_requester, created_at)
                VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), 'SUPPORT', ?, 'AI_SUGGESTION',
                    'fixture', UUID_TO_BIN(?), ?, TRUE, ?)
                """, id.toString(), tenant, ticket.toString(), SEEDED_ADMIN.toString(), suggestion.toString(),
                id.toString(), utc(created));
        jdbc.update("UPDATE ai_suggestion SET source_message_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",
                id.toString(), suggestion.toString());
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static AgentOpsPrincipal admin(String tenant) {
        return new AgentOpsPrincipal(SEEDED_ADMIN, tenant, "admin", "Admin", null, null, Set.of("ADMIN"), Set.of());
    }
}
