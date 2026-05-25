package com.cpayment.payment.infra.webhook;

import com.cpayment.payment.infra.persistence.jpa.WebhookOutboxEntity;
import com.cpayment.payment.infra.persistence.jpa.WebhookOutboxJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Single-row webhook delivery, runnable in any thread. Lives in its own bean so
 * Spring's transaction proxy actually wraps {@link #deliverOne} — the previous
 * {@code WebhookDispatcher.deliverOne} was self-invoked from the same class
 * and therefore <em>not</em> transactional, a subtle bug uncovered while
 * splitting out parallel delivery.
 */
@Component
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final String SIGNATURE_HEADER = "X-Cpayment-Signature";
    private static final String EVENT_ID_HEADER  = "X-Cpayment-Event-Id";

    private final WebhookOutboxJpaRepository outbox;
    private final MerchantRegistry merchants;
    private final WebhookSigner signer;
    private final MerchantWebhookProperties props;
    private final Clock clock;
    private final RestClient http;

    private final Counter delivered;
    private final Counter failed;
    private final Counter retried;

    public WebhookDeliveryService(WebhookOutboxJpaRepository outbox,
                                  MerchantRegistry merchants,
                                  WebhookSigner signer,
                                  MerchantWebhookProperties props,
                                  Clock clock,
                                  MeterRegistry meters) {
        this.outbox = outbox;
        this.merchants = merchants;
        this.signer = signer;
        this.props = props;
        this.clock = clock;

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) props.effectiveConnectTimeout().toMillis());
        rf.setReadTimeout((int)    props.effectiveReadTimeout().toMillis());
        this.http = RestClient.builder().requestFactory(rf).build();

        this.delivered = Counter.builder("cpayment.webhook.delivered").register(meters);
        this.failed    = Counter.builder("cpayment.webhook.failed").register(meters);
        this.retried   = Counter.builder("cpayment.webhook.retried").register(meters);
    }

    @Transactional
    public void deliverOne(WebhookOutboxEntity row) {
        var endpoint = merchants.lookup(row.getMerchantId());
        if (endpoint.isEmpty()) {
            recordFailure(row, "no webhook endpoint configured for merchant " + row.getMerchantId(), true);
            return;
        }
        String signature = signer.sign(row.getPayload(), endpoint.get().secret());
        try {
            http.post().uri(endpoint.get().url())
                .header("Content-Type", "application/json")
                .header(SIGNATURE_HEADER, signature)
                .header(EVENT_ID_HEADER, row.getId().toString())
                .body(row.getPayload())
                .retrieve()
                .toBodilessEntity();
            recordSuccess(row);
        } catch (RestClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            boolean terminal = sc >= 400 && sc < 500;
            recordFailure(row, sc + " " + ex.getStatusText(), terminal);
        } catch (RuntimeException ex) {
            recordFailure(row, ex.getClass().getSimpleName() + ": " + ex.getMessage(), false);
        }
    }

    private void recordSuccess(WebhookOutboxEntity row) {
        row.setStatus(WebhookOutboxEntity.Status.DELIVERED);
        row.setAttempts(row.getAttempts() + 1);
        row.setUpdatedAt(clock.instant());
        outbox.save(row);
        delivered.increment();
        log.info("webhook.delivered id={} merchant={} event={}",
            row.getId(), row.getMerchantId(), row.getEventType());
    }

    private void recordFailure(WebhookOutboxEntity row, String reason, boolean terminal) {
        int attempts = row.getAttempts() + 1;
        row.setAttempts(attempts);
        row.setLastError(reason.length() > 500 ? reason.substring(0, 500) : reason);
        Instant now = clock.instant();
        row.setUpdatedAt(now);

        boolean exhausted = attempts >= props.effectiveMaxAttempts();
        if (terminal || exhausted) {
            row.setStatus(WebhookOutboxEntity.Status.FAILED);
            failed.increment();
            log.warn("webhook.failed id={} merchant={} attempts={} terminal={} reason={}",
                row.getId(), row.getMerchantId(), attempts, terminal, reason);
        } else {
            Duration backoff = computeBackoff(attempts);
            row.setNextAttemptAt(now.plus(backoff));
            retried.increment();
            log.info("webhook.retry id={} merchant={} attempts={} nextIn={}s reason={}",
                row.getId(), row.getMerchantId(), attempts, backoff.toSeconds(), reason);
        }
        outbox.save(row);
    }

    private Duration computeBackoff(int attempts) {
        long seconds = (long) Math.min(props.effectiveMaxBackoff().toSeconds(), Math.pow(2, attempts));
        return Duration.ofSeconds(Math.max(1, seconds));
    }
}
