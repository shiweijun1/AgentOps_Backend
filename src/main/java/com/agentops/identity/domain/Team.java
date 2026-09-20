package com.agentops.identity.domain;

import com.agentops.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "team")
public class Team extends BaseAuditableEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TeamStatus status;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Team() {
    }

    public boolean isActive() {
        return status == TeamStatus.ACTIVE;
    }
}
