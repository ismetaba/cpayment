package com.cpayment.custody.infra.cusserver.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable token + expiry snapshot. {@link #isExpiringSoon} adds a refresh skew so that
 * a token approaching expiry is renewed before the next outbound call uses it.
 */
record CachedAccessToken(String token, Instant expiresAt) {

    private static final Duration REFRESH_SKEW = Duration.ofSeconds(60);

    boolean isExpiringSoon(Instant now) {
        return !now.isBefore(expiresAt.minus(REFRESH_SKEW));
    }
}
