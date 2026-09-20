package com.agentops.analytics.api;

import com.agentops.analytics.application.AnalyticsOverviewService;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "租户级运营指标，仅管理员可访问")
public class AnalyticsController {
    private final AnalyticsOverviewService service;

    public AnalyticsController(AnalyticsOverviewService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @PreAuthorize("principal.roles().contains('ADMIN')")
    @Operation(summary = "运营指标概览", description = "ISO-8601 带时区时间；UTC 半开区间 [from,to)，最长 90 天。比率范围 0～1。")
    public ApiResponse<AnalyticsOverviewResponse> overview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal AgentOpsPrincipal principal
    ) {
        return ApiResponse.success(service.overview(from, to, principal));
    }
}
