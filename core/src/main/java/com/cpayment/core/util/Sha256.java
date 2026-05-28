package com.cpayment.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 helper shared by the idempotency request-hash builders across bounded
 * contexts. Centralised so the algorithm and hex encoding are defined in exactly one
 * place rather than copy-pasted into every {@code *RequestHash}.
 */
public final class Sha256 {

    private Sha256() {}

    /** Lowercase hex of the SHA-256 digest of {@code input}, encoded as UTF-8. */
    public static String hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
