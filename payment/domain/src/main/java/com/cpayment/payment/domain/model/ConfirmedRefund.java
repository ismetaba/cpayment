package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ConfirmedRefund(
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
    int confirmations,
    BigInteger feeActual,
    AssetId feeAsset,
    Instant createdAt,
    Instant updatedAt
) implements Refund {

    public ConfirmedRefund {
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
            throw new IllegalArgumentException("txHash must be non-blank in ConfirmedRefund");
        if (confirmations < 0) throw new IllegalArgumentException("confirmations >= 0");
        Objects.requireNonNull(feeActual, "feeActual");
        Objects.requireNonNull(feeAsset, "feeAsset");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override public RefundStatus status() { return RefundStatus.CONFIRMED; }
}
