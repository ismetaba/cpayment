package com.cpayment.payment.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI configuration. Exposes:
 * <ul>
 *   <li>{@code /v3/api-docs}        — OpenAPI JSON</li>
 *   <li>{@code /swagger-ui.html}    — interactive UI</li>
 * </ul>
 *
 * <p>A dedicated group narrows the spec to the merchant-facing {@code /api/v1/*}
 * endpoints, keeping Actuator routes (which would expose internal Prometheus / health
 * scaffolding) out of the published contract.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cpaymentOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("cpayment API")
                .version("0.1.0")
                .description("Crypto payments product. Custody operations are proxied through a "
                    + "provider-agnostic adapter (currently TÜBİTAK BİLGEM cus-server).")
                .contact(new Contact().name("cpayment team").email("ismetaba@outlook.com"))
                .license(new License().name("Proprietary")));
    }

    @Bean
    public GroupedOpenApi merchantApi() {
        return GroupedOpenApi.builder()
            .group("merchant")
            .displayName("Merchant API (public)")
            .pathsToMatch("/api/v1/**")
            .build();
    }
}
