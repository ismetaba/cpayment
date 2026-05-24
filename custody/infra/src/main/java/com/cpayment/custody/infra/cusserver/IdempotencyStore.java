package com.cpayment.custody.infra.cusserver;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;

import java.util.Optional;

/**
 * Adapter-local idempotency. Because cus-server has no Idempotency-Key support,
 * the adapter is solely responsible for collapsing retries.
 *
 * <p>Implementations must be transactionally safe — i.e. the (key, returned TransferId)
 * row must be persisted in the SAME transaction as any state that would lead to a
 * re-submit, or before the cus-server call returns.
 */
public interface IdempotencyStore {

    Optional<TransferId> findByKey(IdempotencyKey key, String requestHash);

    void record(IdempotencyKey key, String requestHash, TransferId transferId);
}
