package com.agentops.agent.api;

import com.agentops.agent.application.AgentQueryService;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Agent Analysis", description = "工单智能分析运行、步骤与结果")
public class AgentController {
    private final AgentQueryService service;
    public AgentController(AgentQueryService service) { this.service = service; }

    @GetMapping("/tickets/{ticketId}/agent-runs")
    @PreAuthorize("hasAuthority('agent:read')")
    @Operation(summary = "查询工单 Agent 运行记录")
    public ApiResponse<List<AgentRunResponse>> list(@PathVariable UUID ticketId,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.list(ticketId, principal).stream().map(AgentRunResponse::from).toList());
    }

    @GetMapping("/agent-runs/{runId}")
    @PreAuthorize("hasAuthority('agent:read')")
    @Operation(summary = "查询 Agent 运行详情")
    public ApiResponse<AgentRunResponse> get(@PathVariable UUID runId,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(AgentRunResponse.from(service.get(runId, principal)));
    }

    @GetMapping("/agent-runs/{runId}/steps")
    @PreAuthorize("hasAuthority('agent:read')")
    @Operation(summary = "查询 Agent 执行步骤")
    public ApiResponse<List<AgentStepResponse>> steps(@PathVariable UUID runId,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.steps(runId, principal).stream().map(AgentStepResponse::from).toList());
    }

    @GetMapping("/tickets/{ticketId}/analysis")
    @PreAuthorize("hasAuthority('agent:read')")
    @Operation(summary = "查询最新成功的工单分析结果")
    public ApiResponse<AnalysisResponse> analysis(@PathVariable UUID ticketId,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.analysis(ticketId, principal).map(AnalysisResponse::from).orElse(null));
    }

    @PostMapping("/tickets/{ticketId}/agent-runs")
    @PreAuthorize("hasAuthority('agent:rerun')")
    @Operation(summary = "人工发起重新分析")
    public ResponseEntity<ApiResponse<AgentRunResponse>> rerun(@PathVariable UUID ticketId,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(AgentRunResponse.from(service.rerun(ticketId, principal))));
    }
}
