package com.cpayment.custody.domain.port;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;

import java.util.Optional;

/**
 * Secondary port — persistent two-phase idempotency for the cus-server transfer
 * adapter. Lives in {@code custody.domain.port} so concrete implementations may
 * live in any module without breaking the ArchUnit "payment must not depend on
 * custody.infra" rule. cus-server has no native Idempotency-Key support, so the
 * adapter relies on this port to dedupe retries across process restarts.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #beginClaim} — atomic. Returns the cached {@link TransferId}
 *       (COMPLETED), claims the key in PENDING and returns empty (NEW), or throws
 *       {@link IdempotencyInProgressException} (PENDING) /
 *       {@link IdempotencyConflictException} (hash mismatch).</li>
 *   <li>cus-server POST.</li>
 *   <li>On success: {@link #completeClaim} transitions PENDING → COMPLETED.</li>
 *   <li>On failure: {@link #releaseClaim} removes the PENDING entry so retries proceed.</li>
 * </ol>
 *
 * <p>The previous name {@code IdempotencyStore} was renamed to
 * {@code TransferIdempotencyStore} to disambiguate from the per-resource invoice /
 * payout / refund stores in the payment domain.
 */
public interface TransferIdempotencyStore {

    Optional<TransferId> beginClaim(IdempotencyKey key, String requestHash);

    void completeClaim(IdempotencyKey key, String requestHash, TransferId transferId);

    void releaseClaim(IdempotencyKey key, String requestHash);
}
