package com.cpayment.payment.infra.web;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.IssueRefundCommand;
import com.cpayment.payment.domain.model.RefundReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

public record IssueRefundRequest(
    @NotNull @Positive BigInteger amount,
    @NotBlank String fromAddress,
    @NotBlank String toAddress,
    String memo,
    @NotNull RefundReason reason
) {

    public IssueRefundCommand toCommand(UUID invoiceId, String idempotencyKey) {
        return new IssueRefundCommand(
            IdempotencyKey.of(idempotencyKey),
            InvoiceId.of(invoiceId),
            amount,
            fromAddress,
            toAddress,
            memo == null || memo.isBlank() ? Optional.empty() : Optional.of(memo),
            reason
        );
    }
}
