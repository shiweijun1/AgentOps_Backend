package com.agentops.identity.api;

import com.agentops.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration", description = "管理员权限验证接口")
public class AdminSecurityController {

    @GetMapping("/security-check")
    @PreAuthorize("hasAuthority('admin:security-check')")
    @Operation(summary = "验证管理员权限", description = "需要 admin:security-check 权限")
    public ApiResponse<SecurityCheckResponse> securityCheck() {
        return ApiResponse.success(new SecurityCheckResponse(
                "security check passed",
                "admin:security-check"
        ));
    }
}
