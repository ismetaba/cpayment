package com.cpayment.custody.infra.cusserver.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "cpayment.custody.cusserver")
public record CusServerProperties(
    @NotBlank String baseUrl,
    @NotBlank String holderJwtIssuerUrl,
    @NotBlank String holderClientId,
    @NotBlank String holderClientSecret,
    @NotNull Timeouts timeouts,
    @NotNull Rabbit rabbit
) {

    /** Per-call HTTP timeouts. Defaults are conservative; tune per environment. */
    public record Timeouts(
        @NotNull Duration connect,
        @NotNull Duration read
    ) {
        public Timeouts {
            if (connect.isNegative() || connect.isZero()) {
                throw new IllegalArgumentException("connect timeout must be positive");
            }
            if (read.isNegative() || read.isZero()) {
                throw new IllegalArgumentException("read timeout must be positive");
            }
        }
    }

    public record Rabbit(
        String host,
        int port,
        String virtualHost,
        String username,
        String password,
        String createDepositQueue,
        String updateTransactionQueue
    ) {}
}
