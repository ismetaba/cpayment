package com.cpayment.payment.domain.usecase;

import com.cpayment.core.util.Sha256;
import com.cpayment.payment.domain.model.IssueRefundCommand;

/** Canonical SHA-256 of the refund request — used by the two-phase idempotency store. */
public final class RefundRequestHash {

    private RefundRequestHash() {}

    public static String of(IssueRefundCommand cmd) {
        return Sha256.hex(String.join("|",
            cmd.invoiceId().value().toString(),
            cmd.amount().toString(),
            cmd.fromAddress(),
            cmd.toAddress(),
            cmd.reason().name(),
            cmd.memo().orElse("")
        ));
    }
}
