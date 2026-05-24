package com.cpayment.payment.domain.model;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AssetId;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

public record CreatePayoutCommand(
    IdempotencyKey idempotencyKey,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    Optional<String> memo
) {

    public CreatePayoutCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(fromAddress, "fromAddress");
        if (fromAddress.isBlank()) throw new IllegalArgumentException("fromAddress must be non-blank");
        Objects.requireNonNull(toAddress, "toAddress");
        if (toAddress.isBlank()) throw new IllegalArgumentException("toAddress must be non-blank");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (memo == null) memo = Optional.empty();
    }
}
