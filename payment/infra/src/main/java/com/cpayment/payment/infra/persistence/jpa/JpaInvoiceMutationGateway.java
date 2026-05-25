package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceEvent;
import com.cpayment.payment.domain.port.InvoiceMutationGateway;
import com.cpayment.payment.infra.webhook.WebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Atomic invoice update + outbox enqueue. The whole method runs in a single transaction
 * — Spring's {@code @Transactional} default — so a deliverable webhook event can never
 * exist for an invoice state that wasn't committed, and vice versa.
 */
@Component
public class JpaInvoiceMutationGateway implements InvoiceMutationGateway {

    private final InvoiceJpaRepository invoices;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public JpaInvoiceMutationGateway(InvoiceJpaRepository invoices,
                                     OutboxWriter outbox,
                                     ObjectMapper objectMapper) {
        this.invoices = invoices;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void apply(Invoice updated, List<InvoiceEvent> events) {
        invoices.save(InvoiceMapper.toEntity(updated));
        for (InvoiceEvent event : events) {
            outbox.enqueue(
                event.invoice().id().value(),
                WebhookOutboxEntity.ResourceType.INVOICE,
                event.invoice().merchantId().value(),
                event.type().name(),
                eventId -> serialize(eventId, event)
            );
        }
    }

    private String serialize(java.util.UUID eventId, InvoiceEvent event) {
        try {
            return objectMapper.writeValueAsString(WebhookPayload.of(eventId, event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "failed to serialize webhook payload for " + event.invoice().id().value(), e);
        }
    }
}
