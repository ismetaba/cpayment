package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Cus-server accepted the request; tx not yet on chain. From this state the payout can
 * advance to {@link BroadcastPayout}, {@link FailedPayout}, {@link ReplacedPayout},
 * or {@link CancelledPayout}.
 */
public record SubmittedPayout(
    PayoutId id,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    Optional<String> memo,
    TransferId custodyTransferId,
    Instant createdAt,
    Instant updatedAt
) implements Payout {

    public SubmittedPayout {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        if (fromAddress == null || fromAddress.isBlank())
            throw new IllegalArgumentException("fromAddress must be non-blank");
        if (toAddress == null || toAddress.isBlank())
            throw new IllegalArgumentException("toAddress must be non-blank");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        Objects.requireNonNull(memo, "memo");
        Objects.requireNonNull(custodyTransferId, "custodyTransferId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Factory for a brand-new submission. */
    public static SubmittedPayout fresh(PayoutId id, MerchantId merchant, AssetId asset,
                                        String fromAddress, String toAddress, BigInteger amount,
                                        Optional<String> memo, TransferId transferId, Instant now) {
        return new SubmittedPayout(id, merchant, asset, fromAddress, toAddress, amount,
            memo, transferId, now, now);
    }

    @Override public PayoutStatus status() { return PayoutStatus.SUBMITTED; }

    // --- transitions ---

    public BroadcastPayout broadcast(String txHash, Instant now) {
        return new BroadcastPayout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            custodyTransferId, txHash, createdAt, now);
    }

    public FailedPayout fail(FailureReason reason, String message, Instant now) {
        return new FailedPayout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            custodyTransferId, Optional.empty(), reason, message, createdAt, now);
    }

    public ReplacedPayout replaceWith(TransferId newTransferId, Instant now) {
        return new ReplacedPayout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            custodyTransferId, newTransferId, createdAt, now);
    }

    public CancelledPayout cancel(Instant now) {
        return new CancelledPayout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            custodyTransferId, createdAt, now);
    }
}
