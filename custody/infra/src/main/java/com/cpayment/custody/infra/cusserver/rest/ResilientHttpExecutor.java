package com.cpayment.custody.infra.cusserver.rest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Wraps outbound cus-server calls with retry + circuit-breaker policies.
 *
 * <h2>Policy decisions</h2>
 * <ul>
 *   <li><b>Retry is GETS ONLY.</b> POST against cus-server is NOT idempotent (no
 *       Idempotency-Key support); retrying would create duplicate accounts. Callers
 *       must explicitly choose {@link #get} vs {@link #write}.</li>
 *   <li><b>Retry only on 5xx and network errors.</b> 4xx is a client mistake — retrying
 *       won't change the answer.</li>
 *   <li><b>Circuit breaker wraps everything.</b> When cus-server is hard-down, fail
 *       fast across all operations rather than keep timing out.</li>
 *   <li>Exponential backoff with jitter, capped at 5 attempts over &lt;=8s.</li>
 * </ul>
 *
 * <p>Metrics are auto-bound to Micrometer so {@code resilience4j_retry_calls_total}
 * and {@code resilience4j_circuitbreaker_state} show up at {@code /actuator/prometheus}.
 */
@Component
public class ResilientHttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResilientHttpExecutor.class);
    private static final String INSTANCE = "cus-server";

    private final Retry getRetry;
    private final CircuitBreaker breaker;
    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry breakerRegistry;
    private final MeterRegistry meters;

    public ResilientHttpExecutor(MeterRegistry meters) {
        this.meters = meters;
        this.retryRegistry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(4)
            .waitDuration(Duration.ofMillis(200))
            .retryOnException(ResilientHttpExecutor::isRetryable)
            .build());
        this.breakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .slowCallRateThreshold(70.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .build());
        this.getRetry = retryRegistry.retry(INSTANCE);
        this.breaker = breakerRegistry.circuitBreaker(INSTANCE);
    }

    @PostConstruct
    void bindMetrics() {
        TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meters);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakerRegistry).bindTo(meters);
    }

    /** Idempotent read operations. Retried + circuit-broken. */
    public <T> T get(Supplier<T> op) {
        Supplier<T> retried = Retry.decorateSupplier(getRetry, op);
        Supplier<T> broken = CircuitBreaker.decorateSupplier(breaker, retried);
        return broken.get();
    }

    /**
     * Non-idempotent write operations. NOT retried — but circuit-broken so a degraded
     * cus-server doesn't pile up timed-out requests.
     */
    public <T> T write(Supplier<T> op) {
        return CircuitBreaker.decorateSupplier(breaker, op).get();
    }

    private static boolean isRetryable(Throwable t) {
        if (t instanceof RestClientResponseException rex) {
            int sc = rex.getStatusCode().value();
            return sc >= 500 && sc < 600;
        }
        // Includes ResourceAccessException (connect/read timeouts, IOException wrappers)
        return true;
    }
}
