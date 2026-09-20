package com.agentops.identity.infrastructure.persistence;

import com.agentops.identity.domain.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findByTenantIdAndUsernameIgnoreCase(String tenantId, String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findByIdAndTenantId(UUID id, String tenantId);
}
