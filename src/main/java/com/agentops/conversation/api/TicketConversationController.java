package com.agentops.conversation.api;

import com.agentops.conversation.application.TicketConversationService;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{id}")
@Tag(name = "Ticket Conversation", description = "工单公开会话、内部备注与人工发送已采纳建议")
public class TicketConversationController {
    private final TicketConversationService service;
    public TicketConversationController(TicketConversationService service) { this.service = service; }

    @GetMapping("/messages")
    @PreAuthorize("hasAuthority('ticket:message:read')")
    @Operation(summary = "按时间顺序查询会话；客户只看到公开消息")
    public ApiResponse<List<TicketMessageResponse>> list(@PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal actor) {
        return ApiResponse.success(service.list(id, actor).stream().map(TicketMessageResponse::from).toList());
    }

    @PostMapping("/messages")
    @PreAuthorize("hasAnyAuthority('ticket:message:reply','ticket:message:note')")
    @Operation(summary = "发送公开回复或添加内部备注；每次必须提供 clientRequestId")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> post(@PathVariable UUID id,
            @Valid @RequestBody PostTicketMessageRequest request,
            @AuthenticationPrincipal AgentOpsPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(TicketMessageResponse.from(
                service.post(id, request.intent(), request.content(), request.clientRequestId(), actor))));
    }

    @PostMapping("/suggestions/{suggestionId}/send")
    @PreAuthorize("hasAuthority('ticket:suggestion:send')")
    @Operation(summary = "将已采纳建议的最终快照作为公开消息发送；仅写平台内会话")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> sendSuggestion(@PathVariable UUID id,
            @PathVariable UUID suggestionId, @Valid @RequestBody SendSuggestionRequest request,
            @AuthenticationPrincipal AgentOpsPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(TicketMessageResponse.from(
                service.sendSuggestion(id, suggestionId, request.clientRequestId(), actor))));
    }
}
