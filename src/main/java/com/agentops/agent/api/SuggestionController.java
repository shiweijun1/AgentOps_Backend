package com.agentops.agent.api;

import com.agentops.agent.application.SuggestionReviewService;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "AI Reply Suggestions", description = "人工查看、编辑、采纳或拒绝带知识引用的回复建议；不会发送给客户")
public class SuggestionController {
    private final SuggestionReviewService service;
    public SuggestionController(SuggestionReviewService service) { this.service = service; }

    @GetMapping("/tickets/{ticketId}/suggestions")
    @PreAuthorize("hasAuthority('suggestion:read')")
    @Operation(summary = "查看工单回复建议")
    public ApiResponse<List<SuggestionReviewService.SuggestionView>> list(@PathVariable UUID ticketId,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.list(ticketId, principal));
    }

    @GetMapping("/suggestions/{id}")
    @PreAuthorize("hasAuthority('suggestion:read')")
    @Operation(summary = "查看回复建议和引用")
    public ApiResponse<SuggestionReviewService.SuggestionView> get(@PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.get(id, principal));
    }

    @PatchMapping("/suggestions/{id}")
    @PreAuthorize("hasAuthority('suggestion:review')")
    @Operation(summary = "编辑建议正文；原始建议保留不变")
    public ApiResponse<SuggestionReviewService.SuggestionView> edit(@PathVariable UUID id,
            @Valid @RequestBody EditRequest request, @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.edit(id, request.expectedVersion(), request.content(), principal));
    }

    @PostMapping("/suggestions/{id}/adopt")
    @PreAuthorize("hasAuthority('suggestion:review')")
    @Operation(summary = "采纳建议并保存最终内容快照；不发送客户消息")
    public ApiResponse<SuggestionReviewService.SuggestionView> adopt(@PathVariable UUID id,
            @Valid @RequestBody VersionRequest request, @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.adopt(id, request.expectedVersion(), principal));
    }

    @PostMapping("/suggestions/{id}/reject")
    @PreAuthorize("hasAuthority('suggestion:review')")
    @Operation(summary = "拒绝建议")
    public ApiResponse<SuggestionReviewService.SuggestionView> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectRequest request, @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.reject(id, request.expectedVersion(), request.reason(), principal));
    }

    public record EditRequest(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(max = 2000) String content) {}
    public record VersionRequest(@NotNull @Min(0) Long expectedVersion) {}
    public record RejectRequest(@NotNull @Min(0) Long expectedVersion,
                                @NotBlank @Size(max = 500) String reason) {}
}
