package com.agentops.knowledge.api;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.knowledge.application.KnowledgeApplicationService;
import com.agentops.knowledge.application.KnowledgeSearchHit;
import com.agentops.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge", description = "知识文章版本管理与当前发布片段全文检索")
public class KnowledgeController {
    private final KnowledgeApplicationService service;
    public KnowledgeController(KnowledgeApplicationService service) { this.service = service; }

    @PostMapping("/articles")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    @Operation(summary = "创建知识文章")
    public ResponseEntity<ApiResponse<KnowledgeArticleResponse>> create(
            @Valid @RequestBody CreateKnowledgeArticleRequest request,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(KnowledgeArticleResponse.from(
                service.createArticle(principal.tenantId(), request.title(), request.validFrom(), request.validUntil()))));
    }

    @PostMapping("/articles/{id}/versions")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    @Operation(summary = "创建文章新草稿版本")
    public ResponseEntity<ApiResponse<KnowledgeVersionResponse>> createVersion(
            @PathVariable UUID id, @Valid @RequestBody CreateKnowledgeVersionRequest request,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(KnowledgeVersionResponse.from(
                service.createVersion(id, principal.tenantId(), request.content(), principal.userId().toString()))));
    }

    @PostMapping("/articles/{id}/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    @Operation(summary = "审核发布版本", description = "分块、版本状态和 currentVersionId 在同一事务中提交")
    public ApiResponse<KnowledgeArticleResponse> publish(@PathVariable UUID id, @PathVariable UUID versionId,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(KnowledgeArticleResponse.from(service.publish(
                id, versionId, principal.tenantId(), principal.userId().toString())));
    }

    @PostMapping("/articles/{id}/withdraw")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    @Operation(summary = "撤回文章")
    public ApiResponse<KnowledgeArticleResponse> withdraw(@PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(KnowledgeArticleResponse.from(service.withdraw(id, principal.tenantId())));
    }

    @GetMapping("/articles/{id}")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    @Operation(summary = "查询文章详情")
    public ApiResponse<KnowledgeArticleResponse> get(@PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(KnowledgeArticleResponse.from(service.getArticle(id, principal.tenantId())));
    }

    @GetMapping("/articles/{id}/versions")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    @Operation(summary = "查询文章版本列表")
    public ApiResponse<List<KnowledgeVersionResponse>> versions(@PathVariable UUID id,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.listVersions(id, principal.tenantId()).stream()
                .map(KnowledgeVersionResponse::from).toList());
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('knowledge:search')")
    @Operation(summary = "检索当前有效的已发布知识片段", description = "MySQL ngram 全文检索，中文建议至少两个字符")
    public ApiResponse<List<KnowledgeSearchHit>> search(@RequestParam("q") @NotBlank String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(service.search(principal.tenantId(), query, limit));
    }
}
