package com.cpayment.payment.infra.webhook;

import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceEvent;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * On-the-wire shape merchants will see. Stable contract — additive changes only.
 *
 * <pre>{@code
 * {
 *   "eventId":         "<random>",
 *   "eventType":       "INVOICE_PAID",
 *   "createdAt":       "2026-05-25T12:00:00Z",
 *   "invoice": {
 *     "id":            "...",
 *     "merchantId":    "...",
 *     "asset":         "eth:mainnet:usdc",
 *     "expectedAmount":"1000000",
 *     "status":        "PAID",
 *     "receivedAmount":"1000000",
 *     "receivedTxHash":"0x..."
 *   }
 * }
 * }</pre>
 */
public record WebhookPayload(
    UUID eventId,
    String eventType,
    Instant createdAt,
    InvoicePayload invoice
) {

    /**
     * Builds the wire payload using the caller-supplied {@code eventId}. The outbox row
     * id is passed in so it matches the {@code X-Cpayment-Event-Id} HTTP header — merchants
     * use a single id to dedupe both the header and the body.
     */
    public static WebhookPayload of(UUID eventId, InvoiceEvent e) {
        Invoice i = e.invoice();
        return new WebhookPayload(
            eventId,
            e.type().name(),
            Instant.now(),
            new InvoicePayload(
                i.id().value(),
                i.merchantId().value(),
                i.asset().canonical(),
                i.expectedAmount(),
                i.status().name(),
                i.receivedAmount().orElse(null),
                i.receivedTxHash().orElse(null),
                i.receivedConfirmations().orElse(null)
            )
        );
    }

    public record InvoicePayload(
        UUID id,
        UUID merchantId,
        String asset,
        BigInteger expectedAmount,
        String status,
        BigInteger receivedAmount,
        String receivedTxHash,
        Integer receivedConfirmations
    ) {}
}
