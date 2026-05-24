package com.cpayment.payment.domain.model;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AssetId;

import java.math.BigInteger;
import java.util.Objects;

public record CreateInvoiceCommand(
    IdempotencyKey idempotencyKey,
    MerchantId merchantId,
    AssetId asset,
    BigInteger expectedAmount
) {

    public CreateInvoiceCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(expectedAmount, "expectedAmount");
        if (expectedAmount.signum() <= 0) {
            throw new IllegalArgumentException("expectedAmount must be positive");
        }
    }
}
