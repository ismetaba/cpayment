package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.model.IssueRefundCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RefundRequestHash {

    private RefundRequestHash() {}

    public static String of(IssueRefundCommand cmd) {
        String canonical = String.join("|",
            cmd.invoiceId().value().toString(),
            cmd.amount().toString(),
            cmd.fromAddress(),
            cmd.toAddress(),
            cmd.reason().name(),
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
