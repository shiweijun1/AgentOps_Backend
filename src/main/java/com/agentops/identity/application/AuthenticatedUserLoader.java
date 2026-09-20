package com.agentops.identity.application;

import com.agentops.identity.domain.UserAccount;
import com.agentops.identity.infrastructure.persistence.UserAccountRepository;
import com.agentops.identity.security.AgentOpsPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthenticatedUserLoader {

    private final UserAccountRepository userAccountRepository;
    private final PrincipalFactory principalFactory;

    public AuthenticatedUserLoader(
            UserAccountRepository userAccountRepository,
            PrincipalFactory principalFactory
    ) {
        this.userAccountRepository = userAccountRepository;
        this.principalFactory = principalFactory;
    }

    @Transactional(readOnly = true)
    public AgentOpsPrincipal load(UUID userId, String tenantId) {
        UserAccount account = userAccountRepository.findByIdAndTenantId(userId, tenantId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BadCredentialsException("Token 对应的用户不可用"));
        return principalFactory.from(account);
    }
}
