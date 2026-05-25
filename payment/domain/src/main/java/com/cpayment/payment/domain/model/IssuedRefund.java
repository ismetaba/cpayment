package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record IssuedRefund(
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
    Instant createdAt,
    Instant updatedAt
) implements Refund {

    public IssuedRefund {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        if (fromAddress == null || fromAddress.isBlank())
            throw new IllegalArgumentException("fromAddress must be non-blank");
        if (toAddress == null || toAddress.isBlank())
            throw new IllegalArgumentException("toAddress must be non-blank");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(memo, "memo");
        Objects.requireNonNull(custodyTransferId, "custodyTransferId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static IssuedRefund fresh(RefundId id, InvoiceId invoiceId, MerchantId merchant,
                                     AssetId asset, String fromAddress, String toAddress,
                                     BigInteger amount, RefundReason reason, Optional<String> memo,
                                     TransferId transferId, Instant now) {
        return new IssuedRefund(id, invoiceId, merchant, asset, fromAddress, toAddress, amount,
            reason, memo, transferId, now, now);
    }

    @Override public RefundStatus status() { return RefundStatus.ISSUED; }

    public BroadcastRefund broadcast(String txHash, Instant now) {
        return new BroadcastRefund(id, invoiceId, merchantId, asset, fromAddress, toAddress,
            amount, reason, memo, custodyTransferId, txHash, createdAt, now);
    }

    public FailedRefund fail(FailureReason failureReason, String message, Instant now) {
        return new FailedRefund(id, invoiceId, merchantId, asset, fromAddress, toAddress,
            amount, reason, memo, custodyTransferId, Optional.empty(), failureReason, message,
            createdAt, now);
    }
}
