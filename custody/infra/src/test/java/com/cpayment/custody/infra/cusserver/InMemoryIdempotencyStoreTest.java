package com.cpayment.custody.infra.cusserver;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIdempotencyStoreTest {

    private static final IdempotencyKey KEY = IdempotencyKey.of("transfer-key-1");
    private static final String HASH_A = "hashA";
    private static final String HASH_B = "hashB";

    private InMemoryIdempotencyStore store;

    @BeforeEach
    void setUp() { store = new InMemoryIdempotencyStore(); }

    @Test
    void begin_on_unknown_key_returns_empty_and_holds_pending() {
        Optional<TransferId> result = store.beginClaim(KEY, HASH_A);
        assertThat(result).isEmpty();

        // Same key + same hash, but the entry is still PENDING → in-progress.
        assertThatThrownBy(() -> store.beginClaim(KEY, HASH_A))
            .isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void complete_then_begin_returns_cached_transfer_id() {
        TransferId tid = TransferId.of(UUID.randomUUID());
        store.beginClaim(KEY, HASH_A);
        store.completeClaim(KEY, HASH_A, tid);

        assertThat(store.beginClaim(KEY, HASH_A)).contains(tid);
    }

    @Test
    void begin_with_different_hash_raises_conflict() {
        store.beginClaim(KEY, HASH_A);
        assertThatThrownBy(() -> store.beginClaim(KEY, HASH_B))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void release_clears_pending_so_retry_proceeds() {
        store.beginClaim(KEY, HASH_A);
        store.releaseClaim(KEY, HASH_A);

        Optional<TransferId> retry = store.beginClaim(KEY, HASH_A);
        assertThat(retry).isEmpty();
    }

    @Test
    void release_does_not_clear_completed_entry() {
        TransferId tid = TransferId.of(UUID.randomUUID());
        store.beginClaim(KEY, HASH_A);
        store.completeClaim(KEY, HASH_A, tid);

        store.releaseClaim(KEY, HASH_A); // should be a no-op against COMPLETED

        assertThat(store.beginClaim(KEY, HASH_A)).contains(tid);
    }
}
