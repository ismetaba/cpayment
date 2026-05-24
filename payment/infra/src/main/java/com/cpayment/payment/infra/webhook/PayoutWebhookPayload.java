package com.cpayment.payment.infra.webhook;

import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * On-the-wire payload for payout webhooks. Stable contract — additive changes only.
 *
 * <pre>{@code
 * {
 *   "eventId":   "<random>",
 *   "eventType": "PAYOUT_CONFIRMED",
 *   "createdAt": "2026-05-25T12:00:00Z",
 *   "payout": {
 *     "id":            "...",
 *     "merchantId":    "...",
 *     "asset":         "eth:mainnet:usdc",
 *     "fromAddress":   "0x...",
 *     "toAddress":     "0x...",
 *     "amount":        "1000000",
 *     "status":        "CONFIRMED",
 *     "transferId":    "...",
 *     "txHash":        "0x...",
 *     "confirmations": 12,
 *     "feeActual":     "21000",
 *     "feeAsset":      "eth:mainnet:eth"
 *   }
 * }
 * }</pre>
 */
public record PayoutWebhookPayload(
    UUID eventId,
    String eventType,
    Instant createdAt,
    PayoutBody payout
) {

    /** Builds the wire payload using the outbox row's UUID as {@code eventId} so it
     *  matches the {@code X-Cpayment-Event-Id} HTTP header (used by merchants to dedupe). */
    public static PayoutWebhookPayload of(UUID eventId, PayoutEvent e) {
        Payout p = e.payout();
        return new PayoutWebhookPayload(
            eventId,
            e.type().name(),
            Instant.now(),
            new PayoutBody(
                p.id().value(),
                p.merchantId().value(),
                p.asset().canonical(),
                p.fromAddress(),
                p.toAddress(),
                p.amount(),
                p.memo().orElse(null),
                p.status().name(),
                p.custodyTransferId().map(t -> t.value()).orElse(null),
                p.txHash().orElse(null),
                p.confirmations().orElse(null),
                p.feeActual().orElse(null),
                p.feeAsset().map(a -> a.canonical()).orElse(null),
                p.failureReason().map(Enum::name).orElse(null),
                p.failureMessage().orElse(null)
            )
        );
    }

    public record PayoutBody(
        UUID id,
        UUID merchantId,
        String asset,
        String fromAddress,
        String toAddress,
        BigInteger amount,
        String memo,
        String status,
        UUID transferId,
        String txHash,
        Integer confirmations,
        BigInteger feeActual,
        String feeAsset,
        String failureReason,
        String failureMessage
    ) {}
}
