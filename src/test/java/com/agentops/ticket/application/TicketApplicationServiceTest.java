package com.agentops.ticket.application;

import com.agentops.identity.application.IdentityDirectory;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.api.AssignTicketRequest;
import com.agentops.ticket.api.CreateTicketRequest;
import com.agentops.ticket.api.TransitionTicketRequest;
import com.agentops.ticket.domain.IdempotencyRecord;
import com.agentops.ticket.domain.IdempotencyStatus;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketAssignmentRecord;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;
import com.agentops.ticket.domain.TicketTransitionRecord;
import com.agentops.ticket.infrastructure.persistence.IdempotencyRecordRepository;
import com.agentops.ticket.infrastructure.persistence.TicketAssignmentRecordRepository;
import com.agentops.ticket.infrastructure.persistence.TicketRepository;
import com.agentops.ticket.infrastructure.persistence.TicketTransitionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketApplicationServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID TEAM_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Mock TicketRepository ticketRepository;
    @Mock TicketAssignmentRecordRepository assignmentRepository;
    @Mock TicketTransitionRecordRepository transitionRepository;
    @Mock IdempotencyRecordRepository idempotencyRepository;
    @Mock IdentityDirectory identityDirectory;
    @Mock DomainEventOutbox domainEventOutbox;

    private TicketApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new TicketApplicationService(
                ticketRepository, assignmentRepository, transitionRepository, idempotencyRepository,
                identityDirectory, new TicketAccessPolicy(), new TicketNumberGenerator(clock),
                domainEventOutbox, clock
        );
    }

    @Test
    void shouldCreateTicketAndCompleteIdempotencyRecord() {
        when(idempotencyRepository.reserve(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(ticketRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket ticket = service.create(
                new CreateTicketRequest("Login issue", "Cannot sign in", null), "create-1", customer()
        );

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        verify(transitionRepository).save(any());
        verify(domainEventOutbox).append(any());
        verify(idempotencyRepository).complete(
                "default", OWNER_ID.toString(), "CREATE_TICKET", "create-1", ticket.getId().toString()
        );
    }

    @Test
    void repeatedIdempotencyKeyShouldReturnOriginalTicket() {
        Ticket original = ticket(OWNER_ID);
        IdempotencyRecord record = org.mockito.Mockito.mock(IdempotencyRecord.class);
        String requestHash = sha256("Login issue\nCannot sign in\nMEDIUM");
        when(idempotencyRepository.reserve(any(), any(), any(), any(), any(), any())).thenReturn(0);
        when(idempotencyRepository.findByTenantIdAndRequesterIdAndOperationTypeAndIdempotencyKey(
                "default", OWNER_ID.toString(), "CREATE_TICKET", "create-1"))
                .thenReturn(Optional.of(record));
        when(record.getRequestHash()).thenReturn(requestHash);
        when(record.getStatus()).thenReturn(IdempotencyStatus.COMPLETED);
        when(record.getResourceId()).thenReturn(original.getId().toString());
        when(ticketRepository.findByIdAndTenantId(original.getId(), "default")).thenReturn(Optional.of(original));

        Ticket replay = service.create(
                new CreateTicketRequest("Login issue", "Cannot sign in", null), "create-1", customer()
        );

        assertThat(replay).isSameAs(original);
    }

    @Test
    void shouldRejectAccessToAnotherUsersTicket() {
        Ticket ticket = ticket(OTHER_ID);
        when(ticketRepository.findByIdAndTenantId(ticket.getId(), "default")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.get(ticket.getId(), customer()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void shouldAssignAndWriteHistoryInTheSameUseCase() {
        Ticket ticket = ticket(OWNER_ID);
        when(ticketRepository.findByIdAndTenantId(ticket.getId(), "default")).thenReturn(Optional.of(ticket));
        when(identityDirectory.requireActiveUser("default", OTHER_ID))
                .thenReturn(new IdentityDirectory.UserReference(OTHER_ID, TEAM_ID));

        Ticket result = service.assign(
                ticket.getId(), new AssignTicketRequest(TEAM_ID, OTHER_ID, "manual route", 0L), admin()
        );

        assertThat(result.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(result.getAssigneeId()).isEqualTo(OTHER_ID);
        ArgumentCaptor<TicketAssignmentRecord> history = ArgumentCaptor.forClass(TicketAssignmentRecord.class);
        verify(assignmentRepository).save(history.capture());
        assertThat(history.getValue().getToAssigneeId()).isEqualTo(OTHER_ID);
        verify(ticketRepository).flush();
    }

    @Test
    void shouldRejectStaleExpectedVersion() {
        Ticket ticket = ticket(OWNER_ID);
        when(ticketRepository.findByIdAndTenantId(ticket.getId(), "default")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.assign(
                ticket.getId(), new AssignTicketRequest(TEAM_ID, null, "route", 9L), admin()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.TICKET_CONCURRENT_MODIFICATION));
    }

    @Test
    void shouldRejectIllegalStatusTransition() {
        Ticket ticket = ticket(OWNER_ID);
        when(ticketRepository.findByIdAndTenantId(ticket.getId(), "default")).thenReturn(Optional.of(ticket));
        when(transitionRepository.findByTenantIdAndTicketIdAndCommandId("default", ticket.getId(), "cmd-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(
                ticket.getId(), new TransitionTicketRequest(TicketStatus.RESOLVED, null, "cmd-1", 0L), admin()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.TICKET_INVALID_TRANSITION));
    }

    @Test
    void shouldTransitionAndWriteHistory() {
        Ticket ticket = ticket(OWNER_ID);
        when(ticketRepository.findByIdAndTenantId(ticket.getId(), "default")).thenReturn(Optional.of(ticket));
        when(transitionRepository.findByTenantIdAndTicketIdAndCommandId("default", ticket.getId(), "cmd-pending"))
                .thenReturn(Optional.empty());

        Ticket result = service.transition(
                ticket.getId(),
                new TransitionTicketRequest(TicketStatus.PENDING, "accepted", "cmd-pending", 0L),
                admin()
        );

        assertThat(result.getStatus()).isEqualTo(TicketStatus.PENDING);
        ArgumentCaptor<TicketTransitionRecord> history = ArgumentCaptor.forClass(TicketTransitionRecord.class);
        verify(transitionRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(history.getValue().getToStatus()).isEqualTo(TicketStatus.PENDING);
        verify(ticketRepository).flush();
    }

    private Ticket ticket(UUID requesterId) {
        return Ticket.create(
                UUID.randomUUID(), "default", "T20300101-TEST", requesterId,
                "Title", "Description", TicketPriority.MEDIUM, "a".repeat(64), Instant.now()
        );
    }

    private AgentOpsPrincipal customer() {
        return new AgentOpsPrincipal(
                OWNER_ID, "default", "customer", "Customer", null, null,
                Set.of("CUSTOMER"), Set.of("ticket:create", "ticket:read:self")
        );
    }

    private AgentOpsPrincipal admin() {
        return new AgentOpsPrincipal(
                OWNER_ID, "default", "admin", "Admin", null, null,
                Set.of("ADMIN"), Set.of("ticket:read:any", "ticket:assign", "ticket:transition")
        );
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
