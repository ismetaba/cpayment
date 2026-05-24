package com.cpayment.payment.domain.port;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Invoice;

import java.util.Optional;

/**
 * Two-phase idempotency store. The pattern closes the orphan-account crash window:
 * the claim is recorded BEFORE the side-effecting custody call, so a process restart
 * between the custody success and the local invoice save does NOT cause a retry to
 * create a second custody account.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #beginClaim} — atomic. Either returns a cached invoice (already COMPLETED),
 *       or claims the key in PENDING state and returns {@link Optional#empty()}.</li>
 *   <li>The caller does its side-effecting work.</li>
 *   <li>On success: {@link #completeClaim} transitions PENDING → COMPLETED.</li>
 *   <li>On failure BEFORE side-effects: {@link #releaseClaim} removes the PENDING entry
 *       so retries are unblocked.</li>
 *   <li>On failure AFTER side-effects: the caller must NOT release. The claim stays
 *       PENDING; subsequent retries get {@link IdempotencyInProgressException} until an
 *       operator reconciles.</li>
 * </ol>
 *
 * <h2>Concurrency</h2>
 * <p>{@code beginClaim} must be linearizable — concurrent calls with the same key see
 * at most one winner; losers observe the winner's state (Pending or Completed) and react.
 *
 * @throws IdempotencyConflictException if the key was previously used with a different
 *         request payload.
 * @throws IdempotencyInProgressException if the key is currently claimed (Pending).
 */
public interface InvoiceIdempotencyStore {

    Optional<Invoice> beginClaim(IdempotencyKey key, String requestHash);

    void completeClaim(IdempotencyKey key, String requestHash, Invoice invoice);

    void releaseClaim(IdempotencyKey key, String requestHash);
}
