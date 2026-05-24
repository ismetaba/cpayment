package com.cpayment.payment.domain.port;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Invoice;

import java.util.Optional;

/**
 * Outbound port — caches the result of a successful invoice creation against a
 * client-supplied idempotency key.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #findExisting} returns the cached {@link Invoice} only when the
 *       caller-presented {@code requestHash} matches the originally-recorded one.
 *       A mismatch must raise
 *       {@link com.cpayment.core.exception.IdempotencyConflictException}
 *       — clients that re-use a key with a different payload are buggy and must be told.</li>
 *   <li>{@link #record} stores the (key, hash, invoice) tuple atomically. Implementations
 *       MUST detect concurrent inserts and either return the existing record or fail with
 *       a conflict exception to prevent two invoices from sharing a key.</li>
 * </ul>
 */
public interface InvoiceIdempotencyStore {

    Optional<Invoice> findExisting(IdempotencyKey key, String requestHash);

    void record(IdempotencyKey key, String requestHash, Invoice invoice);
}
