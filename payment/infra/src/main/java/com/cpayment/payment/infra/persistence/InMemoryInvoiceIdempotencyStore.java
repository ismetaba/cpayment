package com.cpayment.payment.infra.persistence;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Placeholder implementation backed by an in-memory map. Production replacement should
 * be a JPA-backed store with a unique constraint on {@code key} so that concurrent
 * inserts surface a constraint violation that this class maps to
 * {@link IdempotencyConflictException}.
 *
 * <p>{@link ConcurrentMap#putIfAbsent} is used so that two threads racing on the same
 * key produce exactly one winning entry; the loser observes the winner's record and
 * raises a conflict if its hash differs.
 */
@Component
public class InMemoryInvoiceIdempotencyStore implements InvoiceIdempotencyStore {

    private record Entry(String requestHash, Invoice invoice) {}

    private final ConcurrentMap<IdempotencyKey, Entry> table = new ConcurrentHashMap<>();

    @Override
    public Optional<Invoice> findExisting(IdempotencyKey key, String requestHash) {
        Entry e = table.get(key);
        if (e == null) return Optional.empty();
        if (!e.requestHash.equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
        return Optional.of(e.invoice);
    }

    @Override
    public void record(IdempotencyKey key, String requestHash, Invoice invoice) {
        Entry incoming = new Entry(requestHash, invoice);
        Entry existing = table.putIfAbsent(key, incoming);
        if (existing != null && !existing.requestHash.equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
    }
}
