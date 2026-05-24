package com.cpayment.payment.infra.persistence;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.MerchantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryInvoiceIdempotencyStoreTest {

    private static final IdempotencyKey KEY = IdempotencyKey.of("k-1");
    private static final String HASH_A = "hashA";
    private static final String HASH_B = "hashB";

    private InMemoryInvoiceIdempotencyStore store;

    @BeforeEach
    void setUp() { store = new InMemoryInvoiceIdempotencyStore(); }

    @Test
    void begin_on_unknown_key_returns_empty_and_holds_pending() {
        Optional<Invoice> result = store.beginClaim(KEY, HASH_A);
        assertThat(result).isEmpty();

        // Concurrent begin with the same hash sees PENDING.
        assertThatThrownBy(() -> store.beginClaim(KEY, HASH_A))
            .isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void complete_then_begin_returns_cached_invoice() {
        Invoice invoice = sampleInvoice();
        store.beginClaim(KEY, HASH_A);
        store.completeClaim(KEY, HASH_A, invoice);

        Optional<Invoice> hit = store.beginClaim(KEY, HASH_A);
        assertThat(hit).contains(invoice);
    }

    @Test
    void begin_with_different_hash_raises_conflict() {
        store.beginClaim(KEY, HASH_A);
        assertThatThrownBy(() -> store.beginClaim(KEY, HASH_B))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void completed_entry_with_different_hash_raises_conflict() {
        Invoice invoice = sampleInvoice();
        store.beginClaim(KEY, HASH_A);
        store.completeClaim(KEY, HASH_A, invoice);

        assertThatThrownBy(() -> store.beginClaim(KEY, HASH_B))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void release_clears_pending_so_retry_proceeds() {
        store.beginClaim(KEY, HASH_A);
        store.releaseClaim(KEY, HASH_A);

        Optional<Invoice> retry = store.beginClaim(KEY, HASH_A);
        assertThat(retry).isEmpty();
    }

    @Test
    void release_with_wrong_hash_is_no_op() {
        store.beginClaim(KEY, HASH_A);
        store.releaseClaim(KEY, HASH_B);

        assertThatThrownBy(() -> store.beginClaim(KEY, HASH_A))
            .isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void release_does_not_clear_completed_entry() {
        Invoice invoice = sampleInvoice();
        store.beginClaim(KEY, HASH_A);
        store.completeClaim(KEY, HASH_A, invoice);

        store.releaseClaim(KEY, HASH_A);

        Optional<Invoice> hit = store.beginClaim(KEY, HASH_A);
        assertThat(hit).contains(invoice);
    }

    private static Invoice sampleInvoice() {
        return Invoice.newlyCreated(
            InvoiceId.newId(),
            MerchantId.of(UUID.randomUUID()),
            new AssetId(new NetworkId("eth", "mainnet"), "usdc"),
            BigInteger.TEN,
            AccountId.of(UUID.randomUUID()),
            "0xADDR",
            Instant.parse("2026-05-25T12:00:00Z")
        );
    }
}
