package com.cpayment.payment.infra.web;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.payment.domain.model.CreateInvoiceCommand;
import com.cpayment.payment.domain.model.MerchantId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID merchantId,
    @NotBlank String asset,
    @NotNull @Positive BigInteger expectedAmount
) {

    public CreateInvoiceCommand toCommand(String idempotencyKey) {
        return new CreateInvoiceCommand(
            IdempotencyKey.of(idempotencyKey),
            MerchantId.of(merchantId),
            AssetId.parse(asset),
            expectedAmount
        );
    }
}
