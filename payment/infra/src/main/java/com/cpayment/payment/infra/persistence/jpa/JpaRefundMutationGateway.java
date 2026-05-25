package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundEvent;
import com.cpayment.payment.domain.port.RefundMutationGateway;
import com.cpayment.payment.infra.webhook.RefundWebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Atomic refund save + outbox enqueue. Mirrors the invoice/payout gateways. */
@Component
public class JpaRefundMutationGateway implements RefundMutationGateway {

    private final RefundJpaRepository refunds;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public JpaRefundMutationGateway(RefundJpaRepository refunds,
                                    OutboxWriter outbox,
                                    ObjectMapper objectMapper) {
        this.refunds = refunds;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void apply(Refund updated, List<RefundEvent> events) {
        refunds.save(RefundMapper.toEntity(updated));
        for (RefundEvent event : events) {
            outbox.enqueue(
                event.refund().id().value(),
                WebhookOutboxEntity.ResourceType.REFUND,
                event.refund().merchantId().value(),
                event.type().name(),
                eventId -> serialize(eventId, event)
            );
        }
    }

    private String serialize(UUID eventId, RefundEvent event) {
        try {
            return objectMapper.writeValueAsString(RefundWebhookPayload.of(eventId, event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "failed to serialize refund webhook payload for " + event.refund().id().value(), e);
        }
    }
}
