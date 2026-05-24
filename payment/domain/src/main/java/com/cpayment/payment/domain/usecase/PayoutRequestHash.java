package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.model.CreatePayoutCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical SHA-256 of the payout request — used by the two-phase idempotency store. */
public final class PayoutRequestHash {

    private PayoutRequestHash() {}

    public static String of(CreatePayoutCommand cmd) {
        String canonical = String.join("|",
            cmd.merchantId().value().toString(),
            cmd.asset().canonical(),
            cmd.fromAddress(),
            cmd.toAddress(),
            cmd.amount().toString(),
            cmd.memo().orElse("")
        );
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
