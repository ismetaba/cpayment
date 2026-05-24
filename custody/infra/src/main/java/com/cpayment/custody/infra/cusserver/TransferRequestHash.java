package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.SendTransferCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical SHA-256 over the contract-relevant fields of a transfer request. Used by
 * the adapter-local idempotency store to detect "same key, different body" collisions
 * before forwarding to cus-server (which has no native idempotency support).
 */
final class TransferRequestHash {

    private TransferRequestHash() {}

    static String of(SendTransferCommand cmd) {
        String canonical = String.join("|",
            cmd.fromAccount().value().toString(),
            cmd.fromAddress(),
            cmd.toAddress(),
            cmd.asset().canonical(),
            cmd.amount().toString(),
            cmd.memo().orElse(""),
            cmd.feePreference().getClass().getSimpleName()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
