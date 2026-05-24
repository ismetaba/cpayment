package com.cpayment.payment.infra.persistence;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory two-phase idempotency store. Production replacement: a JPA-backed table
 * with a unique constraint on {@code key} and a {@code state CHECK ('PENDING','COMPLETED')}
 * column so the database enforces atomicity.
 *
 * <p>All transitions go through {@link ConcurrentMap#compute} so the test+swap is atomic
 * under contention. A claim is sealed inside {@link Entry} to make the two states
 * exhaustively pattern-matched.
 */
@Component
public class InMemoryInvoiceIdempotencyStore implements InvoiceIdempotencyStore {

    private sealed interface Entry permits Pending, Completed {
        String requestHash();
    }
    private record Pending(String requestHash) implements Entry {}
    private record Completed(String requestHash, Invoice invoice) implements Entry {}

    private final ConcurrentMap<IdempotencyKey, Entry> table = new ConcurrentHashMap<>();

    @Override
    public Optional<Invoice> beginClaim(IdempotencyKey key, String requestHash) {
        var holder = new Object() { Optional<Invoice> result = Optional.empty(); };

        table.compute(key, (k, existing) -> {
            if (existing == null) {
                return new Pending(requestHash);
            }
            if (!existing.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                    "idempotency key reused with a different request payload: " + key.value());
            }
            // Same hash → either still pending or already completed.
            return switch (existing) {
                case Pending p -> {
                    throw new IdempotencyInProgressException(
                        "idempotency key currently in flight: " + key.value());
                }
                case Completed c -> {
                    holder.result = Optional.of(c.invoice());
                    yield c;  // unchanged
                }
            };
        });

        return holder.result;
    }

    @Override
    public void completeClaim(IdempotencyKey key, String requestHash, Invoice invoice) {
        table.compute(key, (k, existing) -> {
            if (existing == null) {
                // Defensive: completing without a prior claim should never happen
                // through the use case, but persistently we'd accept and stamp it.
                return new Completed(requestHash, invoice);
            }
            if (!existing.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                    "completeClaim hash mismatch for key " + key.value());
            }
            // Idempotent: completing an already-completed entry with the same hash is a no-op.
            return existing instanceof Completed ? existing : new Completed(requestHash, invoice);
        });
    }

    @Override
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        table.compute(key, (k, existing) -> {
            if (existing == null) return null;
            // Only release a Pending entry with matching hash. Releasing a Completed entry
            // — or one belonging to a different request — would be a programming error.
            if (existing instanceof Pending p && p.requestHash().equals(requestHash)) {
                return null;
            }
            return existing;
        });
    }
}
