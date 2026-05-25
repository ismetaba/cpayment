package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Terminal — superseded by a new transfer (RBF / speed-up). */
public record ReplacedPayout(
    PayoutId id,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    Optional<String> memo,
    TransferId custodyTransferId,
    TransferId replacedBy,
    Instant createdAt,
    Instant updatedAt
) implements Payout {

    public ReplacedPayout {
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
        Objects.requireNonNull(replacedBy, "replacedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public PayoutStatus status() { return PayoutStatus.REPLACED; }
}
