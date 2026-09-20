package com.agentops.identity.application;

import com.agentops.identity.domain.Team;
import com.agentops.identity.domain.UserAccount;
import com.agentops.identity.infrastructure.persistence.TeamRepository;
import com.agentops.identity.infrastructure.persistence.UserAccountRepository;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class IdentityDirectory {

    private final UserAccountRepository userRepository;
    private final TeamRepository teamRepository;

    public IdentityDirectory(UserAccountRepository userRepository, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public UserReference requireActiveUser(String tenantId, UUID userId) {
        UserAccount user = userRepository.findByIdAndTenantId(userId, tenantId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "目标用户不存在或不可用"));
        return new UserReference(user.getId(), user.getTeamId());
    }

    @Transactional(readOnly = true)
    public void requireActiveTeam(String tenantId, UUID teamId) {
        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .filter(Team::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "目标团队不存在或不可用"));
    }

    public record UserReference(UUID id, UUID teamId) {
    }
}
