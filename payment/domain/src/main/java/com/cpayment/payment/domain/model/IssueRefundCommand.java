package com.cpayment.payment.domain.model;

import com.cpayment.core.model.IdempotencyKey;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Caller-supplied refund instruction. {@code fromAddress} is the merchant's payout source
 * (usually warm wallet); {@code toAddress} is wherever the customer wants the funds — the
 * merchant decides, cpayment doesn't infer it from the original deposit's sender because
 * crypto refund destinations are sometimes different (exchange withdrawal, new wallet).
 */
public record IssueRefundCommand(
    IdempotencyKey idempotencyKey,
    InvoiceId invoiceId,
    BigInteger amount,
    String fromAddress,
    String toAddress,
    Optional<String> memo,
    RefundReason reason
) {

    public IssueRefundCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (fromAddress == null || fromAddress.isBlank())
            throw new IllegalArgumentException("fromAddress must be non-blank");
        if (toAddress == null || toAddress.isBlank())
            throw new IllegalArgumentException("toAddress must be non-blank");
        if (memo == null) memo = Optional.empty();
        Objects.requireNonNull(reason, "reason");
    }
}
