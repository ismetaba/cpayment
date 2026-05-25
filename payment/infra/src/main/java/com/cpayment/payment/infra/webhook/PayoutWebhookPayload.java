package com.cpayment.payment.infra.webhook;

import com.cpayment.payment.domain.model.BroadcastPayout;
import com.cpayment.payment.domain.model.CancelledPayout;
import com.cpayment.payment.domain.model.ConfirmedPayout;
import com.cpayment.payment.domain.model.FailedPayout;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.ReplacedPayout;
import com.cpayment.payment.domain.model.SubmittedPayout;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * On-the-wire payload for payout webhooks. Stable contract — additive changes only.
 *
 * <p>The body always carries every field of the underlying {@link Payout} variant
 * — variant-specific fields are populated, the rest are {@code null} so merchants
 * receive a consistent JSON shape regardless of lifecycle position.
 */
public record PayoutWebhookPayload(
    UUID eventId,
    String eventType,
    Instant createdAt,
    PayoutBody payout
) {

    /** Builds the wire payload with the outbox row UUID as {@code eventId} so it
     *  matches the {@code X-Cpayment-Event-Id} HTTP header. */
    public static PayoutWebhookPayload of(UUID eventId, PayoutEvent e) {
        return new PayoutWebhookPayload(eventId, e.type().name(), Instant.now(), bodyOf(e.payout()));
    }

    private static PayoutBody bodyOf(Payout p) {
        PayoutBody.Builder b = PayoutBody.builder()
            .id(p.id().value())
            .merchantId(p.merchantId().value())
            .asset(p.asset().canonical())
            .fromAddress(p.fromAddress())
            .toAddress(p.toAddress())
            .amount(p.amount())
            .memo(p.memo().orElse(null))
            .status(p.status().name())
            .transferId(p.custodyTransferId().value());

        switch (p) {
            case SubmittedPayout s -> { /* nothing extra */ }
            case BroadcastPayout x -> b.txHash(x.txHash());
            case ConfirmedPayout c -> b
                .txHash(c.txHash())
                .confirmations(c.confirmations())
                .feeActual(c.feeActual())
                .feeAsset(c.feeAsset().canonical());
            case FailedPayout f -> b
                .txHash(f.txHash().orElse(null))
                .failureReason(f.failureReason().name())
                .failureMessage(f.failureMessage());
            case ReplacedPayout r -> b.replacedBy(r.replacedBy().value());
            case CancelledPayout x -> { /* nothing extra */ }
        }
        return b.build();
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
        String failureMessage,
        UUID replacedBy
    ) {

        static Builder builder() { return new Builder(); }

        static final class Builder {
            private UUID id; private UUID merchantId; private String asset;
            private String fromAddress; private String toAddress; private BigInteger amount;
            private String memo; private String status; private UUID transferId;
            private String txHash; private Integer confirmations; private BigInteger feeActual;
            private String feeAsset; private String failureReason; private String failureMessage;
            private UUID replacedBy;

            Builder id(UUID v) { this.id = v; return this; }
            Builder merchantId(UUID v) { this.merchantId = v; return this; }
            Builder asset(String v) { this.asset = v; return this; }
            Builder fromAddress(String v) { this.fromAddress = v; return this; }
            Builder toAddress(String v) { this.toAddress = v; return this; }
            Builder amount(BigInteger v) { this.amount = v; return this; }
            Builder memo(String v) { this.memo = v; return this; }
            Builder status(String v) { this.status = v; return this; }
            Builder transferId(UUID v) { this.transferId = v; return this; }
            Builder txHash(String v) { this.txHash = v; return this; }
            Builder confirmations(int v) { this.confirmations = v; return this; }
            Builder feeActual(BigInteger v) { this.feeActual = v; return this; }
            Builder feeAsset(String v) { this.feeAsset = v; return this; }
            Builder failureReason(String v) { this.failureReason = v; return this; }
            Builder failureMessage(String v) { this.failureMessage = v; return this; }
            Builder replacedBy(UUID v) { this.replacedBy = v; return this; }

            PayoutBody build() {
                return new PayoutBody(id, merchantId, asset, fromAddress, toAddress, amount,
                    memo, status, transferId, txHash, confirmations, feeActual, feeAsset,
                    failureReason, failureMessage, replacedBy);
            }
        }
    }
}
