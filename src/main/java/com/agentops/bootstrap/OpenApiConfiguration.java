package com.agentops.bootstrap;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI agentOpsOpenApi() {
        String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        bearerScheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .info(new Info()
                        .title("AgentOps API")
                        .version("v1")
                        .description("AgentOps 企业智能工单平台 API。当前版本提供 JWT/RBAC，以及工单创建、查询、分派、状态流转与历史追踪。"));
    }
}
