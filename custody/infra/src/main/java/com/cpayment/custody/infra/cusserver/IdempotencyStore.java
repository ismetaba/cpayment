package com.cpayment.custody.infra.cusserver;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;

import java.util.Optional;

/**
 * Two-phase idempotency store for the cus-server transfer adapter. Mirrors the
 * pattern used by payment-side {@code InvoiceIdempotencyStore} / {@code PayoutIdempotencyStore}
 * so a process crash between cus-server's accept-and-id-return and the adapter's
 * local record cannot let a retry create a duplicate transfer.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #beginClaim} — atomic. Returns the cached {@link TransferId}
 *       (COMPLETED), claims the key in PENDING and returns empty (NEW), or throws
 *       {@link IdempotencyInProgressException} (PENDING) / {@link IdempotencyConflictException}
 *       (hash mismatch).</li>
 *   <li>cus-server POST.</li>
 *   <li>On success: {@link #completeClaim} transitions PENDING → COMPLETED.</li>
 *   <li>On failure: {@link #releaseClaim} removes the PENDING entry so retries proceed.</li>
 * </ol>
 */
public interface IdempotencyStore {

    Optional<TransferId> beginClaim(IdempotencyKey key, String requestHash);

    void completeClaim(IdempotencyKey key, String requestHash, TransferId transferId);

    void releaseClaim(IdempotencyKey key, String requestHash);
}
