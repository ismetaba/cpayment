package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Terminal failure. {@link #txHash} is {@link Optional} because failure may have occurred
 * before broadcast (policy reject, balance insufficient) or after (on-chain revert).
 */
public record FailedPayout(
    PayoutId id,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    Optional<String> memo,
    TransferId custodyTransferId,
    Optional<String> txHash,
    FailureReason failureReason,
    String failureMessage,
    Instant createdAt,
    Instant updatedAt
) implements Payout {

    public FailedPayout {
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
        Objects.requireNonNull(txHash, "txHash (use Optional.empty())");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(failureMessage, "failureMessage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public PayoutStatus status() { return PayoutStatus.FAILED; }
}
