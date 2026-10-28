package com.example.adventurebook.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI adventureBookOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Adventure Book API")
                        .description("REST API for the interactive adventure book engine")
                        .version("1.0.0"));
    }
}
