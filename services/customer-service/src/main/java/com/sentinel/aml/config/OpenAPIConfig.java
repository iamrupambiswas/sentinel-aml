package com.sentinel.aml.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI sentinelOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel AML - Customer Service API")
                        .description("Enterprise Anti-Money Laundering Customer Onboarding & Profile Management")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Sentinel Engineering")
                                .email("rupambiswas953@gmail.com")));
    }
}