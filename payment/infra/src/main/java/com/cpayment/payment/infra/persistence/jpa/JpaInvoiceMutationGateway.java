package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceEvent;
import com.cpayment.payment.domain.port.InvoiceMutationGateway;
import com.cpayment.payment.infra.webhook.WebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Atomic invoice update + outbox enqueue. The whole method runs in a single transaction
 * — Spring's {@code @Transactional} default — so a deliverable webhook event can never
 * exist for an invoice state that wasn't committed, and vice versa.
 */
@Component
public class JpaInvoiceMutationGateway implements InvoiceMutationGateway {

    private final InvoiceJpaRepository invoices;
    private final WebhookOutboxJpaRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JpaInvoiceMutationGateway(InvoiceJpaRepository invoices,
                                     WebhookOutboxJpaRepository outbox,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.invoices = invoices;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void apply(Invoice updated, List<InvoiceEvent> events) {
        invoices.save(InvoiceMapper.toEntity(updated));

        Instant now = clock.instant();
        for (InvoiceEvent event : events) {
            WebhookOutboxEntity row = new WebhookOutboxEntity();
            row.setId(UUID.randomUUID());
            row.setInvoiceId(event.invoice().id().value());
            row.setMerchantId(event.invoice().merchantId().value());
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

    private String serialize(InvoiceEvent event) {
        try {
            return objectMapper.writeValueAsString(WebhookPayload.from(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize webhook payload for "
                + event.invoice().id().value(), e);
        }
    }
}
