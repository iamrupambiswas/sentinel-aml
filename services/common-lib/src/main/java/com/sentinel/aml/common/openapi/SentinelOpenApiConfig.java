package com.sentinel.aml.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * One OpenAPI description per service, shown together in the gateway's Swagger UI (Phase 7b).
 *  - "servers" points at the gateway, so "Try it out" goes through rate limiting and token checks like any client
 *  - a bearer-JWT scheme adds the "Authorize" button: paste a Keycloak access token once for every call
 */
@Configuration
public class SentinelOpenApiConfig {

    @Bean
    public OpenAPI sentinelOpenApi(@Value("${spring.application.name:sentinel}") String service,
                                   @Value("${sentinel.openapi.gateway-url:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel AML · " + service)
                        .description("Called through the gateway. Click Authorize and paste a Keycloak access token "
                                + "(Postman folder \"Auth - get a token\", or the curl command in the manual guide).")
                        .version("v1"))
                .servers(List.of(new Server().url(gatewayUrl).description("API gateway")))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
