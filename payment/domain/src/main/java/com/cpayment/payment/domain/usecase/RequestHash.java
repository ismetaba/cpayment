package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.model.CreateInvoiceCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic hash of a request payload — used by idempotency stores to detect
 * "same key, different body" conflicts.
 *
 * <p>The canonical string concatenates fields in a fixed order with a delimiter that
 * cannot appear inside any of them (UUIDs and BigInteger toString are safe).
 * SHA-256 is overkill for collision avoidance here but trivial to compute and well
 * understood — preferred over hashCode() which collides too easily and varies by JVM.
 */
public final class RequestHash {

    private RequestHash() {}

    public static String of(CreateInvoiceCommand command) {
        String canonical = String.join("|",
            command.merchantId().value().toString(),
            command.asset().canonical(),
            command.expectedAmount().toString()
        );
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
