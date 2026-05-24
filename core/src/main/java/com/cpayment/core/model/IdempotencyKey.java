package com.cpayment.core.model;

import java.util.Objects;

/**
 * Caller-supplied key used to dedupe operations across retries. Length is bounded to
 * keep DB index sizes predictable.
 *
 * <p>Lives in {@code core} because both custody (transfers) and payment (invoices)
 * use the same concept; the meaning of "duplicate" is contextual and lives in each
 * bounded context's idempotency store.
 */
public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("idempotency key must be 1.." + MAX_LENGTH + " chars");
        }
    }

    public static IdempotencyKey of(String v) { return new IdempotencyKey(v); }
}
