package com.agentops.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentops.identity")
public record IdentityProperties(String defaultTenantId) {

    public IdentityProperties {
        if (defaultTenantId == null || defaultTenantId.isBlank()) {
            defaultTenantId = "default";
        }
    }
}
