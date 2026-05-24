package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.infra.webhook.PayoutWebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Atomic payout save + outbox enqueue. Same pattern as
 * {@link JpaInvoiceMutationGateway}; both write into the shared
 * {@code cpayment_webhook_outbox} table — the dispatcher delivers bytes-as-bytes,
 * agnostic of which domain produced them.
 */
@Component
public class JpaPayoutMutationGateway implements PayoutMutationGateway {

    private final PayoutJpaRepository payouts;
    private final WebhookOutboxJpaRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JpaPayoutMutationGateway(PayoutJpaRepository payouts,
                                    WebhookOutboxJpaRepository outbox,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.payouts = payouts;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void apply(Payout updated, List<PayoutEvent> events) {
        payouts.save(PayoutMapper.toEntity(updated));

        Instant now = clock.instant();
        for (PayoutEvent event : events) {
            WebhookOutboxEntity row = new WebhookOutboxEntity();
            row.setId(UUID.randomUUID());
            row.setInvoiceId(event.payout().id().value()); // re-using the column as "resource_id"
            row.setMerchantId(event.payout().merchantId().value());
            row.setEventType(event.type().name());
            row.setPayload(serialize(event));
            row.setStatus(WebhookOutboxEntity.Status.PENDING);
            row.setAttempts(0);
            row.setNextAttemptAt(now);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            outbox.save(row);
        }
    }

    private String serialize(PayoutEvent event) {
        try {
            return objectMapper.writeValueAsString(PayoutWebhookPayload.from(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize payout webhook payload for "
                + event.payout().id().value(), e);
        }
    }
}
