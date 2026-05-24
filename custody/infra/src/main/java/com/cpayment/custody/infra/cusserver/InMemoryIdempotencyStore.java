package com.cpayment.custody.infra.cusserver;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placeholder implementation. Production must replace this with a JPA-backed store
 * (Oracle table cpayment_idempotency: key PK, request_hash, transfer_id, created_at).
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Entry(String requestHash, TransferId transferId) {}

    private final ConcurrentHashMap<IdempotencyKey, Entry> table = new ConcurrentHashMap<>();

    @Override
    public Optional<TransferId> findByKey(IdempotencyKey key, String requestHash) {
        Entry e = table.get(key);
        if (e == null) return Optional.empty();
        if (!e.requestHash.equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
        return Optional.of(e.transferId);
    }

    @Override
    public void record(IdempotencyKey key, String requestHash, TransferId transferId) {
        table.put(key, new Entry(requestHash, transferId));
    }
}
