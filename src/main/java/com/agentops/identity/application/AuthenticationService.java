package com.agentops.identity.application;

import com.agentops.identity.api.LoginRequest;
import com.agentops.identity.api.LoginResponse;
import com.agentops.identity.config.IdentityProperties;
import com.agentops.identity.domain.UserAccount;
import com.agentops.identity.infrastructure.persistence.UserAccountRepository;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.identity.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthenticationService {

    private static final String INVALID_CREDENTIALS = "用户名或密码错误";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PrincipalFactory principalFactory;
    private final JwtService jwtService;
    private final IdentityProperties identityProperties;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            PrincipalFactory principalFactory,
            JwtService jwtService,
            IdentityProperties identityProperties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.principalFactory = principalFactory;
        this.jwtService = jwtService;
        this.identityProperties = identityProperties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String tenantId = normalizeTenant(request.tenantId());
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        UserAccount account = userAccountRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, username)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));

        if (!account.isActive() || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        AgentOpsPrincipal principal = principalFactory.from(account);
        JwtService.IssuedToken token = jwtService.issueAccessToken(principal);
        return new LoginResponse("Bearer", token.value(), token.expiresInSeconds(), token.expiresAt());
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank()
                ? identityProperties.defaultTenantId()
                : tenantId.trim();
    }
}
