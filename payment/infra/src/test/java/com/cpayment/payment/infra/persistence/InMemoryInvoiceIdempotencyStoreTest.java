package com.cpayment.payment.infra.persistence;

import com.cpayment.core.exception.IdempotencyConflictException;
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
    void returns_empty_when_key_unknown() {
        assertThat(store.findExisting(KEY, HASH_A)).isEmpty();
    }

    @Test
    void returns_cached_invoice_when_key_and_hash_match() {
        Invoice invoice = sampleInvoice();
        store.record(KEY, HASH_A, invoice);

        Optional<Invoice> found = store.findExisting(KEY, HASH_A);
        assertThat(found).contains(invoice);
    }

    @Test
    void raises_conflict_on_same_key_with_different_hash() {
        store.record(KEY, HASH_A, sampleInvoice());

        assertThatThrownBy(() -> store.findExisting(KEY, HASH_B))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void raises_conflict_when_record_attempts_overwrite_with_different_hash() {
        store.record(KEY, HASH_A, sampleInvoice());

        assertThatThrownBy(() -> store.record(KEY, HASH_B, sampleInvoice()))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void second_record_with_same_hash_is_a_no_op() {
        Invoice first = sampleInvoice();
        store.record(KEY, HASH_A, first);

        Invoice second = sampleInvoice();  // different invoice instance but same hash
        store.record(KEY, HASH_A, second);

        // first writer wins (putIfAbsent semantics)
        assertThat(store.findExisting(KEY, HASH_A)).contains(first);
    }

    private static Invoice sampleInvoice() {
        return Invoice.newlyCreated(
            InvoiceId.newId(),
            MerchantId.of(UUID.randomUUID()),
            new AssetId(new NetworkId("eth", "mainnet"), "usdc"),
            BigInteger.TEN,
            AccountId.of(UUID.randomUUID()),
            "0xADDR",
            Instant.parse("2026-05-24T12:00:00Z")
        );
    }
}
