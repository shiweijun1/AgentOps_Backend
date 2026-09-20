package com.agentops.identity.infrastructure.persistence;

import com.agentops.identity.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByIdAndTenantId(UUID id, String tenantId);
}
