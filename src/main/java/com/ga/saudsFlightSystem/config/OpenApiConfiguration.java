package com.ga.saudsFlightSystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI flightSystemOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Saud's Flight System API").version("1.0")
                        .description("Flight booking, airline administration and FAA airplane approvals. "
                                + "Log in, copy the JWT from the response message, then use Authorize. "
                                + "Most endpoints require an active account; role and ownership rules still apply."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer publicAuthenticationOperations() {
        return openApi -> List.of("/auth/users/login", "/auth/users/register/email",
                        "/auth/users/verification")
                .forEach(path -> {
                    var item = openApi.getPaths().get(path);
                    if (item != null && item.getPost() != null) {
                        item.getPost().setSecurity(List.of());
                    }
                });
    }
}
