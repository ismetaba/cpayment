package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record FailedRefund(
    RefundId id,
    InvoiceId invoiceId,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    RefundReason reason,
    Optional<String> memo,
    TransferId custodyTransferId,
    Optional<String> txHash,
    FailureReason failureReason,
    String failureMessage,
    Instant createdAt,
    Instant updatedAt
) implements Refund {

    public FailedRefund {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        if (fromAddress == null || fromAddress.isBlank())
            throw new IllegalArgumentException("fromAddress must be non-blank");
        if (toAddress == null || toAddress.isBlank())
            throw new IllegalArgumentException("toAddress must be non-blank");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(memo, "memo");
        Objects.requireNonNull(custodyTransferId, "custodyTransferId");
        Objects.requireNonNull(txHash, "txHash (use Optional.empty())");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(failureMessage, "failureMessage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public RefundStatus status() { return RefundStatus.FAILED; }
}
