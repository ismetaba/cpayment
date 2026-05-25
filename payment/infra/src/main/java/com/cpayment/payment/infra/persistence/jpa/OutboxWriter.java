package com.cpayment.payment.infra.persistence.jpa;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * Single place that turns a domain event into a {@code cpayment_webhook_outbox} row.
 * Both {@code JpaInvoiceMutationGateway} and {@code JpaPayoutMutationGateway} go
 * through this so the row layout, the eventId-to-payload binding, and the initial
 * delivery schedule are defined exactly once.
 *
 * <p>The {@code payloadFactory} receives the generated {@code eventId} so the JSON
 * body carries the same UUID that lands in the {@code id} column (and the
 * {@code X-Cpayment-Event-Id} HTTP header at dispatch time) — merchants dedupe with
 * a single identifier.
 */
@Component
public class OutboxWriter {

    private final WebhookOutboxJpaRepository outbox;
    private final Clock clock;

    public OutboxWriter(WebhookOutboxJpaRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    public void enqueue(UUID resourceId,
                        WebhookOutboxEntity.ResourceType resourceType,
                        UUID merchantId,
                        String eventType,
                        Function<UUID, String> payloadFactory) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();

        WebhookOutboxEntity row = new WebhookOutboxEntity();
        row.setId(eventId);
        row.setResourceId(resourceId);
        row.setResourceType(resourceType);
        row.setMerchantId(merchantId);
        row.setEventType(eventType);
        row.setPayload(payloadFactory.apply(eventId));
        row.setStatus(WebhookOutboxEntity.Status.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        outbox.save(row);
    }
}
