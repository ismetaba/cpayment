package com.cpayment.payment.domain.port;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Payout;

import java.util.Optional;

/**
 * Two-phase idempotency store for payout creation — same contract as
 * {@code InvoiceIdempotencyStore} but typed to {@link Payout}.
 *
 * <p>Kept separate (rather than reusing the invoice store) because:
 * <ul>
 *   <li>different resource types live in different tables (clearer constraints,
 *       smaller indexes, easier audit);</li>
 *   <li>clients are free to use overlapping idempotency-key strings — the scope is the
 *       resource type, not the key alone.</li>
 * </ul>
 *
 * @throws IdempotencyConflictException if the key was previously used with a different
 *         request payload.
 * @throws IdempotencyInProgressException if the key is currently claimed (PENDING).
 */
public interface PayoutIdempotencyStore {

    Optional<Payout> beginClaim(IdempotencyKey key, String requestHash);

    void completeClaim(IdempotencyKey key, String requestHash, Payout payout);

    void releaseClaim(IdempotencyKey key, String requestHash);
}
