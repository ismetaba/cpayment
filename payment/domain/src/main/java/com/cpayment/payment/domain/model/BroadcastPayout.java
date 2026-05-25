package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Custody has broadcast the transaction; on-chain identifier known. Advances to
 * {@link ConfirmedPayout}, {@link FailedPayout}, or {@link ReplacedPayout}.
 */
public record BroadcastPayout(
    PayoutId id,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    Optional<String> memo,
    TransferId custodyTransferId,
    String txHash,
    Instant createdAt,
    Instant updatedAt
) implements Payout {

    public BroadcastPayout {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        if (fromAddress == null || fromAddress.isBlank())
            throw new IllegalArgumentException("fromAddress must be non-blank");
        if (toAddress == null || toAddress.isBlank())
            throw new IllegalArgumentException("toAddress must be non-blank");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(memo, "memo");
        Objects.requireNonNull(custodyTransferId, "custodyTransferId");
        if (txHash == null || txHash.isBlank())
            throw new IllegalArgumentException("txHash must be non-blank in BroadcastPayout");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public PayoutStatus status() { return PayoutStatus.BROADCAST; }

    public ConfirmedPayout confirm(int confirmations, BigInteger feeActual,
                                   AssetId feeAsset, Instant now) {
        return new ConfirmedPayout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            custodyTransferId, txHash, confirmations, feeActual, feeAsset, createdAt, now);
    }

    public FailedPayout fail(FailureReason reason, String message, Instant now) {
        return new FailedPayout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            custodyTransferId, Optional.of(txHash), reason, message, createdAt, now);
    }

    public ReplacedPayout replaceWith(TransferId newTransferId, Instant now) {
        return new ReplacedPayout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            custodyTransferId, newTransferId, createdAt, now);
    }
}
