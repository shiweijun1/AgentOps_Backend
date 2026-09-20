package com.agentops.identity.api;

import com.agentops.identity.application.AuthenticationService;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "用户登录与当前身份")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "校验 BCrypt 密码并签发 JWT Access Token")
    @SecurityRequirements
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authenticationService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal AgentOpsPrincipal principal) {
        return ApiResponse.success(CurrentUserResponse.from(principal));
    }
}
