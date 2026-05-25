package com.cpayment.payment.infra.webhook;

import com.cpayment.payment.infra.persistence.jpa.WebhookOutboxEntity;
import com.cpayment.payment.infra.persistence.jpa.WebhookOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls {@code cpayment_webhook_outbox} on a fixed cadence and dispatches each due
 * row to {@link WebhookDeliveryService} <em>in parallel</em> via a dedicated
 * {@link Executor}. A single slow or unreachable merchant can no longer block the
 * rest of the batch.
 *
 * <p>Each per-row task starts its own transaction (Spring proxy on
 * {@code deliverOne}), so failures are isolated — one row's rollback never affects
 * another.
 *
 * <p>The dispatch tick waits for all in-flight deliveries with a deadline tied to
 * the per-merchant HTTP timeouts, so two ticks can never stack up and exhaust the
 * pool. Stuck deliveries are logged and left to the next tick.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookOutboxJpaRepository outbox;
    private final WebhookDeliveryService delivery;
    private final Executor executor;
    private final MerchantWebhookProperties props;
    private final Clock clock;

    public WebhookDispatcher(WebhookOutboxJpaRepository outbox,
                             WebhookDeliveryService delivery,
                             @Qualifier("webhookDeliveryExecutor") Executor executor,
                             MerchantWebhookProperties props,
                             Clock clock) {
        this.outbox = outbox;
        this.delivery = delivery;
        this.executor = executor;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${cpayment.webhook.poll-interval-millis:5000}")
    public void dispatch() {
        List<WebhookOutboxEntity> due = outbox.findDueForDelivery(
            clock.instant(), PageRequest.of(0, props.effectiveBatchSize()));
        if (due.isEmpty()) return;

        CompletableFuture<?>[] futures = due.stream()
            .map(row -> CompletableFuture.runAsync(() -> safelyDeliver(row), executor))
            .toArray(CompletableFuture[]::new);

        Duration waitBudget = props.effectiveReadTimeout()
            .plus(props.effectiveConnectTimeout())
            .plusSeconds(2);
        try {
            CompletableFuture.allOf(futures).get(waitBudget.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("webhook.batch.timeout — {} deliveries still in flight at end of tick",
                futures.length);
        } catch (Exception e) {
            log.warn("webhook.batch.unexpected error: {}", e.getMessage());
        }
    }

    private void safelyDeliver(WebhookOutboxEntity row) {
        try {
            delivery.deliverOne(row);
        } catch (RuntimeException ex) {
            // deliverOne records failure rows itself; this catch is a last-resort belt
            // so an executor task never propagates an exception into the pool's logs.
            log.error("webhook.delivery uncaught for id={}: {}", row.getId(), ex.getMessage(), ex);
        }
    }
}
