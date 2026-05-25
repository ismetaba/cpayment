package com.cpayment.payment.domain.port;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Refund;

import java.util.Optional;

/**
 * Two-phase idempotency for refund issuance. Same contract as
 * {@code InvoiceIdempotencyStore} / {@code PayoutIdempotencyStore}; separate table
 * so per-resource audit queries don't have to filter on a type column.
 */
public interface RefundIdempotencyStore {

    Optional<Refund> beginClaim(IdempotencyKey key, String requestHash);

    void completeClaim(IdempotencyKey key, String requestHash, Refund refund);

    void releaseClaim(IdempotencyKey key, String requestHash);
}
