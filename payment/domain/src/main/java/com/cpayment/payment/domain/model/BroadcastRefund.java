package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record BroadcastRefund(
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
    String txHash,
    Instant createdAt,
    Instant updatedAt
) implements Refund {

    public BroadcastRefund {
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
        if (txHash == null || txHash.isBlank())
            throw new IllegalArgumentException("txHash must be non-blank in BroadcastRefund");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public RefundStatus status() { return RefundStatus.BROADCAST; }

    public ConfirmedRefund confirm(int confirmations, BigInteger feeActual,
                                   AssetId feeAsset, Instant now) {
        return new ConfirmedRefund(id, invoiceId, merchantId, asset, fromAddress, toAddress,
            amount, reason, memo, custodyTransferId, txHash, confirmations, feeActual, feeAsset,
            createdAt, now);
    }

    public FailedRefund fail(FailureReason failureReason, String message, Instant now) {
        return new FailedRefund(id, invoiceId, merchantId, asset, fromAddress, toAddress,
            amount, reason, memo, custodyTransferId, Optional.of(txHash),
            failureReason, message, createdAt, now);
    }
}
