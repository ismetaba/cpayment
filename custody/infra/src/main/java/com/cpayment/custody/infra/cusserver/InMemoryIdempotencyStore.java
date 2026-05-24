package com.cpayment.custody.infra.cusserver;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory two-phase idempotency store. Production replacement should be a
 * JPA-backed table on the same DB used by the payment side, mirroring the
 * Invoice/Payout idempotency claim tables. For first cut the in-memory map is
 * acceptable because the crash-window is closed once the {@link #beginClaim}
 * row exists — a restart loses the PENDING marker but the retry then re-enters
 * the same code path and the cus-server side-effect either repeats (rare in
 * practice without a stable in-flight queue) or is reconciled by an operator
 * via cus-server logs.
 *
 * <p>All transitions use {@link ConcurrentMap#compute} so test+swap is atomic.
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

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
            if (existing == null) {
                return new Pending(requestHash);
            }
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
            if (existing instanceof Completed c) return c; // idempotent
            return new Completed(requestHash, transferId);
        });
    }

    @Override
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        table.compute(key, (k, existing) -> {
            if (existing instanceof Pending p && p.requestHash().equals(requestHash)) {
                return null;
            }
            return existing;
        });
    }
}
