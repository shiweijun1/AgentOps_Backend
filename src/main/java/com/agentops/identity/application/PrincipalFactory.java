package com.agentops.identity.application;

import com.agentops.identity.domain.Permission;
import com.agentops.identity.domain.Role;
import com.agentops.identity.domain.UserAccount;
import com.agentops.identity.security.AgentOpsPrincipal;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

@Component
public class PrincipalFactory {

    public AgentOpsPrincipal from(UserAccount account) {
        Set<Role> activeRoles = account.getRoles().stream()
                .filter(Role::isActive)
                .collect(java.util.stream.Collectors.toSet());

        Set<String> roleCodes = activeRoles.stream()
                .map(Role::getCode)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        Set<String> permissionCodes = activeRoles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        return new AgentOpsPrincipal(
                account.getId(),
                account.getTenantId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getEmail(),
                account.getTeamId(),
                roleCodes,
                permissionCodes
        );
    }
}
