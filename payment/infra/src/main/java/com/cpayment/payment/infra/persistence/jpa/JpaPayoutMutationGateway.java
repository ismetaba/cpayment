package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.infra.webhook.PayoutWebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Atomic payout save + outbox enqueue. Same pattern as
 * {@link JpaInvoiceMutationGateway}; both write into the shared
 * {@code cpayment_webhook_outbox} table via {@link OutboxWriter}.
 */
@Component
public class JpaPayoutMutationGateway implements PayoutMutationGateway {

    private final PayoutJpaRepository payouts;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public JpaPayoutMutationGateway(PayoutJpaRepository payouts,
                                    OutboxWriter outbox,
                                    ObjectMapper objectMapper) {
        this.payouts = payouts;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void apply(Payout updated, List<PayoutEvent> events) {
        payouts.save(PayoutMapper.toEntity(updated));
        for (PayoutEvent event : events) {
            outbox.enqueue(
                event.payout().id().value(),
                WebhookOutboxEntity.ResourceType.PAYOUT,
                event.payout().merchantId().value(),
                event.type().name(),
                eventId -> serialize(eventId, event)
            );
        }
    }

    private String serialize(UUID eventId, PayoutEvent event) {
        try {
            return objectMapper.writeValueAsString(PayoutWebhookPayload.of(eventId, event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "failed to serialize payout webhook payload for " + event.payout().id().value(), e);
        }
    }
}
