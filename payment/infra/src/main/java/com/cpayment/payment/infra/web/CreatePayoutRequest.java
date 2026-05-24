package com.cpayment.payment.infra.web;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.payment.domain.model.CreatePayoutCommand;
import com.cpayment.payment.domain.model.MerchantId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

public record CreatePayoutRequest(
    @NotNull UUID merchantId,
    @NotBlank String asset,
    @NotBlank String fromAddress,
    @NotBlank String toAddress,
    @NotNull @Positive BigInteger amount,
    String memo
) {

    public CreatePayoutCommand toCommand(String idempotencyKey) {
        return new CreatePayoutCommand(
            IdempotencyKey.of(idempotencyKey),
            MerchantId.of(merchantId),
            AssetId.parse(asset),
            fromAddress,
            toAddress,
            amount,
            memo == null || memo.isBlank() ? Optional.empty() : Optional.of(memo)
        );
    }
}
