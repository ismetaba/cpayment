package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Terminal success — required depth reached, fee known. */
public record ConfirmedPayout(
    PayoutId id,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    Optional<String> memo,
    TransferId custodyTransferId,
    String txHash,
    int confirmations,
    BigInteger feeActual,
    AssetId feeAsset,
    Instant createdAt,
    Instant updatedAt
) implements Payout {

    public ConfirmedPayout {
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
            throw new IllegalArgumentException("txHash must be non-blank in ConfirmedPayout");
        if (confirmations < 0)
            throw new IllegalArgumentException("confirmations must be >= 0");
        Objects.requireNonNull(feeActual, "feeActual");
        Objects.requireNonNull(feeAsset, "feeAsset");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public PayoutStatus status() { return PayoutStatus.CONFIRMED; }
}
