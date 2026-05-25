package com.cpayment.custody.infra.cusserver;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferIdempotencyStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory two-phase store. Test-only — production wires
 * {@code JpaCustodyIdempotencyStore} (in payment-infra) instead. Not a Spring
 * {@code @Component} on purpose: keeping it out of the bean graph guarantees a
 * production build can never accidentally fall back to in-memory storage and
 * lose dedupe state on restart (which would let gas-funder + retry loops
 * double-send to cus-server).
 *
 * <p>All transitions use {@link ConcurrentMap#compute} so test+swap is atomic.
 */
public class InMemoryTransferIdempotencyStore implements TransferIdempotencyStore {

    private sealed interface Entry permits Pending, Completed {
        String requestHash();
    }
    private record Pending(String requestHash) implements Entry {}
    private record Completed(String requestHash, TransferId transferId) implements Entry {}

    private final ConcurrentMap<IdempotencyKey, Entry> table = new ConcurrentHashMap<>();

    @Override
    public Optional<TransferId> beginClaim(IdempotencyKey key, String requestHash) {
        var holder = new Object() { Optional<TransferId> result = Optional.empty(); };

        table.compute(key, (k, existing) -> {
            if (existing == null) return new Pending(requestHash);
            if (!existing.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                    "idempotency key reused with a different request payload: " + key.value());
            }
            return switch (existing) {
                case Pending p -> {
                    throw new IdempotencyInProgressException(
                        "idempotency key currently in flight: " + key.value());
                }
                case Completed c -> {
                    holder.result = Optional.of(c.transferId());
                    yield c;
                }
            };
        });

        return holder.result;
    }

    @Override
    public void completeClaim(IdempotencyKey key, String requestHash, TransferId transferId) {
        table.compute(key, (k, existing) -> {
            if (existing != null && !existing.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                    "completeClaim hash mismatch for key " + key.value());
            }
            if (existing instanceof Completed c) return c;
            return new Completed(requestHash, transferId);
        });
    }

    @Override
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        table.compute(key, (k, existing) -> {
            if (existing instanceof Pending p && p.requestHash().equals(requestHash)) return null;
            return existing;
        });
    }
}
