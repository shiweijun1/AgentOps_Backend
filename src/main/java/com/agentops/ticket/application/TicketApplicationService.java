package com.agentops.ticket.application;

import com.agentops.identity.application.IdentityDirectory;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.shared.api.TraceIds;
import com.agentops.ticket.api.AssignTicketRequest;
import com.agentops.ticket.api.CreateTicketRequest;
import com.agentops.ticket.api.TransitionTicketRequest;
import com.agentops.ticket.domain.AssignmentType;
import com.agentops.ticket.domain.IdempotencyRecord;
import com.agentops.ticket.domain.IdempotencyStatus;
import com.agentops.ticket.domain.IllegalTicketTransitionException;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketActorType;
import com.agentops.ticket.domain.TicketAssignmentRecord;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;
import com.agentops.ticket.domain.TicketTransitionRecord;
import com.agentops.ticket.infrastructure.persistence.IdempotencyRecordRepository;
import com.agentops.ticket.infrastructure.persistence.TicketAssignmentRecordRepository;
import com.agentops.ticket.infrastructure.persistence.TicketRepository;
import com.agentops.ticket.infrastructure.persistence.TicketTransitionRecordRepository;
import com.agentops.ticket.event.TicketCreatedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TicketApplicationService {

    private static final String CREATE_OPERATION = "CREATE_TICKET";

    private final TicketRepository ticketRepository;
    private final TicketAssignmentRecordRepository assignmentRepository;
    private final TicketTransitionRecordRepository transitionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final IdentityDirectory identityDirectory;
    private final TicketAccessPolicy accessPolicy;
    private final TicketNumberGenerator numberGenerator;
    private final DomainEventOutbox domainEventOutbox;
    private final Clock clock;

    public TicketApplicationService(
            TicketRepository ticketRepository,
            TicketAssignmentRecordRepository assignmentRepository,
            TicketTransitionRecordRepository transitionRepository,
            IdempotencyRecordRepository idempotencyRepository,
            IdentityDirectory identityDirectory,
            TicketAccessPolicy accessPolicy,
            TicketNumberGenerator numberGenerator,
            DomainEventOutbox domainEventOutbox,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.assignmentRepository = assignmentRepository;
        this.transitionRepository = transitionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.identityDirectory = identityDirectory;
        this.accessPolicy = accessPolicy;
        this.numberGenerator = numberGenerator;
        this.domainEventOutbox = domainEventOutbox;
        this.clock = clock;
    }

    @Transactional
    public Ticket create(CreateTicketRequest request, String idempotencyKey, AgentOpsPrincipal principal) {
        String title = request.title().trim();
        String description = request.description().trim();
        TicketPriority priority = request.priority() == null ? TicketPriority.MEDIUM : request.priority();
        String requestHash = sha256(title + "\n" + description + "\n" + priority.name());
        String requesterId = principal.userId().toString();

        int reserved = idempotencyRepository.reserve(
                UUID.randomUUID().toString(), principal.tenantId(), requesterId,
                CREATE_OPERATION, idempotencyKey, requestHash
        );
        if (reserved == 0) {
            IdempotencyRecord existing = idempotencyRepository
                    .findByTenantIdAndRequesterIdAndOperationTypeAndIdempotencyKey(
                            principal.tenantId(), requesterId, CREATE_OPERATION, idempotencyKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT));
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key 已用于不同的请求内容");
            }
            if (existing.getStatus() != IdempotencyStatus.COMPLETED || existing.getResourceId() == null) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "相同请求正在处理中，请稍后重试");
            }
            return requireTicket(UUID.fromString(existing.getResourceId()), principal.tenantId());
        }

        Instant now = clock.instant();
        Ticket ticket = Ticket.create(
                UUID.randomUUID(), principal.tenantId(), numberGenerator.next(), principal.userId(),
                title, description, priority, sha256(title + "\n" + description), now
        );
        ticketRepository.saveAndFlush(ticket);
        transitionRepository.save(new TicketTransitionRecord(
                UUID.randomUUID(), principal.tenantId(), ticket.getId(), null, TicketStatus.NEW,
                actorType(principal), requesterId, "工单创建", idempotencyKey, now
        ));
        String eventId = UUID.randomUUID().toString();
        String traceId = TraceIds.current() == null ? eventId : TraceIds.current();
        domainEventOutbox.append(new TicketCreatedEvent(
                eventId,
                TicketCreatedEvent.TYPE,
                TicketCreatedEvent.VERSION,
                now,
                traceId,
                principal.tenantId(),
                ticket.getId(),
                ticket.getTicketNo(),
                principal.userId(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getSubmittedAt()
        ));
        idempotencyRepository.complete(
                principal.tenantId(), requesterId, CREATE_OPERATION, idempotencyKey, ticket.getId().toString()
        );
        return ticket;
    }

    @Transactional(readOnly = true)
    public Ticket get(UUID ticketId, AgentOpsPrincipal principal) {
        Ticket ticket = requireTicket(ticketId, principal.tenantId());
        requireVisible(ticket, principal);
        return ticket;
    }

    @Transactional(readOnly = true)
    public Page<Ticket> search(TicketSearchCriteria criteria, int page, int size, AgentOpsPrincipal principal) {
        var specification = TicketSpecifications.visibleTo(principal, criteria, accessPolicy.isAdministrator(principal));
        return ticketRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @Transactional
    public Ticket assign(UUID ticketId, AssignTicketRequest request, AgentOpsPrincipal principal) {
        Ticket ticket = requireTicket(ticketId, principal.tenantId());
        requireVisible(ticket, principal);
        requireExpectedVersion(ticket, request.expectedVersion());
        if (request.teamId() == null && request.assigneeId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "teamId 和 assigneeId 至少提供一个");
        }

        UUID targetTeamId = request.teamId();
        if (targetTeamId != null) {
            identityDirectory.requireActiveTeam(principal.tenantId(), targetTeamId);
        }
        if (request.assigneeId() != null) {
            IdentityDirectory.UserReference assignee = identityDirectory
                    .requireActiveUser(principal.tenantId(), request.assigneeId());
            if (targetTeamId == null) {
                targetTeamId = assignee.teamId();
            } else if (!Objects.equals(targetTeamId, assignee.teamId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "负责人不属于目标团队");
            }
        }
        if (Objects.equals(ticket.getTeamId(), targetTeamId)
                && Objects.equals(ticket.getAssigneeId(), request.assigneeId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "工单已经处于该分派状态");
        }

        boolean initial = ticket.getTeamId() == null && ticket.getAssigneeId() == null;
        Ticket.AssignmentSnapshot before = ticket.assign(targetTeamId, request.assigneeId());
        ticketRepository.flush();
        assignmentRepository.save(new TicketAssignmentRecord(
                UUID.randomUUID(), principal.tenantId(), ticket.getId(),
                initial ? AssignmentType.INITIAL : AssignmentType.REASSIGN,
                before.teamId(), before.assigneeId(), targetTeamId, request.assigneeId(),
                principal.userId().toString(), request.reason().trim(), ticket.getVersion(), clock.instant()
        ));
        return ticket;
    }

    @Transactional
    public Ticket transition(UUID ticketId, TransitionTicketRequest request, AgentOpsPrincipal principal) {
        Ticket ticket = requireTicket(ticketId, principal.tenantId());
        requireVisible(ticket, principal);

        var previousCommand = transitionRepository.findByTenantIdAndTicketIdAndCommandId(
                principal.tenantId(), ticketId, request.commandId());
        if (previousCommand.isPresent()) {
            if (previousCommand.get().getToStatus() != request.toStatus()) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "commandId 已用于其他状态转换");
            }
            return ticket;
        }

        requireExpectedVersion(ticket, request.expectedVersion());
        TicketStatus from;
        try {
            from = ticket.transitionTo(request.toStatus(), accessPolicy.isAdministrator(principal), clock.instant());
        } catch (IllegalTicketTransitionException exception) {
            throw new BusinessException(
                    ErrorCode.TICKET_INVALID_TRANSITION,
                    "不允许从 " + exception.from() + " 转换到 " + exception.to()
            );
        }
        ticketRepository.flush();
        transitionRepository.save(new TicketTransitionRecord(
                UUID.randomUUID(), principal.tenantId(), ticketId, from, request.toStatus(),
                actorType(principal), principal.userId().toString(), normalizeReason(request.reason()),
                request.commandId(), clock.instant()
        ));
        return ticket;
    }

    @Transactional(readOnly = true)
    public List<TicketAssignmentRecord> assignments(UUID ticketId, AgentOpsPrincipal principal) {
        Ticket ticket = requireTicket(ticketId, principal.tenantId());
        requireVisible(ticket, principal);
        return assignmentRepository.findByTenantIdAndTicketIdOrderByOccurredAtAsc(principal.tenantId(), ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketTransitionRecord> transitions(UUID ticketId, AgentOpsPrincipal principal) {
        Ticket ticket = requireTicket(ticketId, principal.tenantId());
        requireVisible(ticket, principal);
        return transitionRepository.findByTenantIdAndTicketIdOrderByOccurredAtAsc(principal.tenantId(), ticketId);
    }

    private Ticket requireTicket(UUID ticketId, String tenantId) {
        return ticketRepository.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
    }

    private void requireVisible(Ticket ticket, AgentOpsPrincipal principal) {
        if (!accessPolicy.canView(ticket, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireExpectedVersion(Ticket ticket, long expectedVersion) {
        if (ticket.getVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.TICKET_CONCURRENT_MODIFICATION);
        }
    }

    private TicketActorType actorType(AgentOpsPrincipal principal) {
        if (accessPolicy.isAdministrator(principal)) {
            return TicketActorType.ADMIN;
        }
        return principal.roles().contains("SUPPORT") ? TicketActorType.SUPPORT : TicketActorType.USER;
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
