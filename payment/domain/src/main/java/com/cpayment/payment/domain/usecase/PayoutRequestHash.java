package com.cpayment.payment.domain.usecase;

import com.cpayment.core.util.Sha256;
import com.cpayment.payment.domain.model.CreatePayoutCommand;

/** Canonical SHA-256 of the payout request — used by the two-phase idempotency store. */
public final class PayoutRequestHash {

    private PayoutRequestHash() {}

    public static String of(CreatePayoutCommand cmd) {
        return Sha256.hex(String.join("|",
            cmd.merchantId().value().toString(),
            cmd.asset().canonical(),
            cmd.fromAddress(),
            cmd.toAddress(),
            cmd.amount().toString(),
            cmd.memo().orElse("")
        ));
    }
}
