package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Terminal — operator-cancelled. Only reachable from {@link SubmittedPayout}
 * (cancel after broadcast is rejected by {@code CancelPayoutUseCase}), so by
 * construction there is no on-chain tx hash to record.
 */
public record CancelledPayout(
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

    public CancelledPayout {
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
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public PayoutStatus status() { return PayoutStatus.CANCELLED; }
}
