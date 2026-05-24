package com.cpayment.core.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Smallest-unit amount paired with a canonical asset identifier.
 * Always stored in the asset's atomic unit (wei, satoshi, drops, etc.) — never floats.
 */
public record Money(BigInteger amount, String assetCanonical) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(assetCanonical, "assetCanonical");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
    }

    public static Money of(BigInteger amount, String assetCanonical) {
        return new Money(amount, assetCanonical);
    }
}
