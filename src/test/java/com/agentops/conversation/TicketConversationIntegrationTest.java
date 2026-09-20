package com.agentops.conversation;

import com.agentops.agent.application.*;
import com.agentops.agent.domain.AiSuggestion;
import com.agentops.agent.infrastructure.persistence.AiSuggestionRepository;
import com.agentops.conversation.application.*;
import com.agentops.conversation.domain.TicketMessageType;
import com.agentops.conversation.infrastructure.persistence.TicketMessageRepository;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.knowledge.application.KnowledgeApplicationService;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.api.AssignTicketRequest;
import com.agentops.ticket.api.CreateTicketRequest;
import com.agentops.ticket.application.TicketApplicationService;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.dev_seed_enabled=true",
        "spring.flyway.placeholders.dev_admin_password_hash=$2a$12$4RjCpvCT7V6ZKG/cEWsbq.UB.s/hHLNDypgrN3CjCd1sQ.yZl1o2S",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "agentops.messaging.enabled=false", "agentops.agent.worker-enabled=false", "agentops.ai.provider=fake"
})
class TicketConversationIntegrationTest {
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID SUPPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Container static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("agentops");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired TicketApplicationService ticketService;
    @Autowired TicketConversationService conversation;
    @Autowired TicketMessageRepository messages;
    @Autowired TicketRepository tickets;
    @Autowired KnowledgeApplicationService knowledge;
    @Autowired AgentRunCreationService creation;
    @Autowired AgentWorker analysisWorker;
    @Autowired ReplySuggestionWorker replyWorker;
    @Autowired AiSuggestionRepository suggestions;
    @Autowired SuggestionReviewService review;
    @Autowired PlatformTransactionManager transactions;

    @Test void publicRepliesNotesCustomerVisibilityAndFirstResponse() {
        Ticket ticket = create("会话可见性");
        ticketService.assign(ticket.getId(), new AssignTicketRequest(TEAM_ID, SUPPORT_ID, "处理", ticket.getVersion()), admin());
        var first = conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "您好，正在排查。", "reply-" + UUID.randomUUID(), support());
        var note = conversation.post(ticket.getId(), MessageIntent.INTERNAL_NOTE,
                "内部排查记录", "note-" + UUID.randomUUID(), support());
        var customerReply = conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "谢谢，请尽快回复。", "customer-" + UUID.randomUUID(), customer());
        var later = conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "问题已处理。", "later-" + UUID.randomUUID(), support());
        assertThat(conversation.list(ticket.getId(), customer())).extracting("id")
                .containsExactly(first.getId(), customerReply.getId(), later.getId());
        assertThat(conversation.list(ticket.getId(), support())).extracting("id")
                .containsExactly(first.getId(), note.getId(), customerReply.getId(), later.getId());
        assertThat(note.isVisibleToRequester()).isFalse();
        assertThat(first.getMessageType()).isEqualTo(TicketMessageType.SUPPORT_REPLY);
        assertThat(first.getSenderId()).isEqualTo(SUPPORT_ID.toString());
        assertThat(customerReply.getMessageType()).isEqualTo(TicketMessageType.CUSTOMER_REPLY);
        assertThat(customerReply.getSenderId()).isEqualTo(CUSTOMER_ID.toString());
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getFirstResponseAt())
                .isEqualTo(first.getCreatedAt());
    }

    @Test void tenantScopeTicketScopeAndRequestIdConflict() {
        Ticket ticket = create("幂等与权限");
        AgentOpsPrincipal otherCustomer = new AgentOpsPrincipal(UUID.randomUUID(), "default", "other", "Other",
                null, null, Set.of("CUSTOMER"), customer().permissions());
        assertThatThrownBy(() -> conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "不能回复", "not-owner", otherCustomer)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> conversation.list(ticket.getId(), otherCustomer))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "不能回复", "no-team", support())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> conversation.post(ticket.getId(), MessageIntent.INTERNAL_NOTE,
                "伪造内部备注", "customer-note", customer())).isInstanceOf(BusinessException.class);
        var first = conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "同一内容", "same-request", customer());
        var replay = conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "同一内容", "same-request", customer());
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(messages.findByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc("default", ticket.getId()))
                .hasSize(1);
        assertThatThrownBy(() -> conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "不同内容", "same-request", customer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
        assertThatThrownBy(() -> conversation.post(ticket.getId(), MessageIntent.PUBLIC_REPLY,
                "同一内容", "same-request", admin()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
        AgentOpsPrincipal otherTenant = new AgentOpsPrincipal(CUSTOMER_ID, "other", "customer", "Other",
                null, null, Set.of("CUSTOMER"), customer().permissions());
        assertThatThrownBy(() -> conversation.list(ticket.getId(), otherTenant))
                .isInstanceOf(BusinessException.class);
    }

    @Test void adoptedSuggestionIsSentOnlyOnceFromFinalSnapshot() {
        var ready = readySuggestion("极光账号登录");
        Ticket ticket = ready.ticket();
        AiSuggestion suggestion = ready.suggestion();
        assertThatThrownBy(() -> conversation.sendSuggestion(ticket.getId(), suggestion.getId(),
                "early-" + UUID.randomUUID(), admin()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        String edited = "请核查极光账号状态。" + citationMarkers(suggestion);
        var edit = review.edit(suggestion.getId(), suggestion.getVersion(), edited, admin());
        review.adopt(suggestion.getId(), edit.version(), admin());
        var sent = conversation.sendSuggestion(ticket.getId(), suggestion.getId(), "send-once", admin());
        var replay = conversation.sendSuggestion(ticket.getId(), suggestion.getId(), "send-once", admin());
        assertThat(replay.getId()).isEqualTo(sent.getId());
        assertThat(sent.getContent()).isEqualTo(edited);
        assertThat(sent.getSourceSuggestionId()).isEqualTo(suggestion.getId());
        assertThat(sent.getMessageType()).isEqualTo(TicketMessageType.AI_SUGGESTION);
        assertThat(suggestions.findById(suggestion.getId()).orElseThrow().getSourceMessageId())
                .isEqualTo(sent.getId());
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getFirstResponseAt())
                .isEqualTo(sent.getCreatedAt());
        assertThat(messages.countByTenantIdAndTicketIdAndSourceSuggestionId(
                "default", ticket.getId(), suggestion.getId())).isEqualTo(1);
        assertThatThrownBy(() -> conversation.sendSuggestion(ticket.getId(), suggestion.getId(),
                "different-request", admin())).isInstanceOf(BusinessException.class);
        Ticket another = create("不同工单");
        assertThatThrownBy(() -> conversation.sendSuggestion(another.getId(), suggestion.getId(),
                "cross-ticket", admin())).isInstanceOf(BusinessException.class);
    }

    @Test void rollbackRevertsMessageSuggestionLinkAndFirstResponse() {
        var ready = readySuggestion("银河账号登录");
        Ticket ticket = ready.ticket();
        AiSuggestion suggestion = ready.suggestion();
        review.adopt(suggestion.getId(), suggestion.getVersion(), admin());
        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            conversation.sendSuggestion(ticket.getId(), suggestion.getId(), "rollback-send", admin());
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(messages.findByTenantIdAndTicketIdAndClientRequestId("default", ticket.getId(), "rollback-send"))
                .isEmpty();
        assertThat(suggestions.findById(suggestion.getId()).orElseThrow().getSourceMessageId()).isNull();
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getFirstResponseAt()).isNull();
    }

    @Test void concurrentSuggestionSendCreatesAtMostOneMessage() throws Exception {
        var ready = readySuggestion("海鸥账号登录");
        Ticket ticket = ready.ticket();
        AiSuggestion suggestion = ready.suggestion();
        review.adopt(suggestion.getId(), suggestion.getVersion(), admin());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> sendAfter(start, ticket.getId(), suggestion.getId(), "concurrent-1"));
            var second = executor.submit(() -> sendAfter(start, ticket.getId(), suggestion.getId(), "concurrent-2"));
            start.countDown();
            Throwable firstError = first.get();
            Throwable secondError = second.get();
            assertThat((firstError == null) ^ (secondError == null)).isTrue();
            Throwable conflict = firstError == null ? secondError : firstError;
            assertThat(conflict).isInstanceOf(BusinessException.class);
        }
        assertThat(messages.countByTenantIdAndTicketIdAndSourceSuggestionId(
                "default", ticket.getId(), suggestion.getId())).isEqualTo(1);
    }

    private Throwable sendAfter(CountDownLatch start, UUID ticketId, UUID suggestionId, String requestId) {
        try {
            start.await();
            conversation.sendSuggestion(ticketId, suggestionId, requestId, admin());
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private String citationMarkers(AiSuggestion suggestion) {
        String original = suggestion.getOriginalContent();
        return original.substring(original.indexOf("[citation:"));
    }

    private Ready readySuggestion(String title) {
        var article = knowledge.createArticle("default", title + "指南", null, null);
        var version = knowledge.createVersion(article.getId(), "default",
                title + "失败时请检查账号状态与双重认证。", "reviewer");
        knowledge.publish(article.getId(), version.getId(), "default", "reviewer");
        Ticket ticket = create(title);
        creation.manual(ticket.getId(), "default");
        analysisWorker.runBatch();
        replyWorker.runBatch();
        AiSuggestion suggestion = suggestions.findByTenantIdAndTicketIdOrderByCreatedAtDesc("default", ticket.getId())
                .getFirst();
        return new Ready(ticket, suggestion);
    }
    private Ticket create(String title) {
        return ticketService.create(new CreateTicketRequest(title, "请协助排查此问题。", TicketPriority.MEDIUM),
                UUID.randomUUID().toString(), customer());
    }
    private AgentOpsPrincipal customer() {
        return new AgentOpsPrincipal(CUSTOMER_ID, "default", "customer", "Customer", null, null,
                Set.of("CUSTOMER"), Set.of("ticket:create", "ticket:read:self",
                        "ticket:message:read", "ticket:message:reply"));
    }
    private AgentOpsPrincipal support() {
        return new AgentOpsPrincipal(SUPPORT_ID, "default", "support", "Support", null, TEAM_ID,
                Set.of("SUPPORT"), Set.of("ticket:read:team", "ticket:message:read",
                        "ticket:message:reply", "ticket:message:note", "ticket:suggestion:send"));
    }
    private AgentOpsPrincipal admin() {
        return new AgentOpsPrincipal(ADMIN_ID, "default", "admin", "Admin", null, null,
                Set.of("ADMIN"), Set.of("ticket:read:any", "ticket:assign", "ticket:message:read",
                        "ticket:message:reply", "ticket:message:note", "ticket:suggestion:send"));
    }
    private record Ready(Ticket ticket, AiSuggestion suggestion) {}
}
