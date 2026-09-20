package com.agentops.ticket.api;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.api.ApiResponse;
import com.agentops.ticket.application.TicketApplicationService;
import com.agentops.ticket.application.TicketSearchCriteria;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "工单创建、查询、分派和状态流转")
public class TicketController {

    private static final String READ_PERMISSION =
            "hasAnyAuthority('ticket:read:self','ticket:read:team','ticket:read:any')";

    private final TicketApplicationService ticketService;

    public TicketController(TicketApplicationService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ticket:create')")
    @Operation(summary = "创建工单", description = "必须提供 Idempotency-Key；同键同请求返回同一工单")
    public ResponseEntity<ApiResponse<TicketResponse>> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        var ticket = ticketService.create(request, idempotencyKey, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(TicketApiMapper.toResponse(ticket)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_PERMISSION)
    @Operation(summary = "查询工单详情")
    public ApiResponse<TicketResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        return ApiResponse.success(TicketApiMapper.toResponse(ticketService.get(id, principal)));
    }

    @GetMapping
    @PreAuthorize(READ_PERMISSION)
    @Operation(summary = "分页查询工单", description = "查询结果自动按当前用户的数据范围过滤")
    public ApiResponse<TicketPageResponse> search(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        var result = ticketService.search(
                new TicketSearchCriteria(status, priority, teamId, assigneeId, keyword),
                page, size, principal
        );
        return ApiResponse.success(new TicketPageResponse(
                result.getContent().stream().map(TicketApiMapper::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        ));
    }

    @PostMapping("/{id}/assignments")
    @PreAuthorize("hasAuthority('ticket:assign')")
    @Operation(summary = "分派工单", description = "使用 expectedVersion 执行乐观并发控制")
    public ApiResponse<TicketResponse> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTicketRequest request,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        return ApiResponse.success(TicketApiMapper.toResponse(ticketService.assign(id, request, principal)));
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAuthority('ticket:transition')")
    @Operation(summary = "流转工单状态", description = "CLOSED 重新打开仅允许 ADMIN 角色")
    public ApiResponse<TicketResponse> transition(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionTicketRequest request,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        return ApiResponse.success(TicketApiMapper.toResponse(ticketService.transition(id, request, principal)));
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize(READ_PERMISSION)
    @Operation(summary = "查询分派历史")
    public ApiResponse<List<TicketAssignmentResponse>> assignments(
            @PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        return ApiResponse.success(ticketService.assignments(id, principal).stream()
                .map(TicketApiMapper::toResponse).toList());
    }

    @GetMapping("/{id}/transitions")
    @PreAuthorize(READ_PERMISSION)
    @Operation(summary = "查询状态流转历史")
    public ApiResponse<List<TicketTransitionResponse>> transitions(
            @PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        return ApiResponse.success(ticketService.transitions(id, principal).stream()
                .map(TicketApiMapper::toResponse).toList());
    }
}
