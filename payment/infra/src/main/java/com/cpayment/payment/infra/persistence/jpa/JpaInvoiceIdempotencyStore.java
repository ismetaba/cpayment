package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed two-phase idempotency store. The {@code cpayment_idempotency_claim} table
 * uses {@code key} as the primary key so concurrent INSERTs collide at the database
 * boundary; we translate the collision into the correct domain exception by re-reading
 * the surviving row.
 *
 * <h2>Transaction boundaries</h2>
 * <p>{@code beginClaim} runs as {@code REQUIRES_NEW READ_COMMITTED} — independent of any
 * outer transaction. This ensures the PENDING row is durable as soon as we return; if
 * the caller's outer transaction later rolls back the side-effects, the claim still
 * stands and a retry sees PENDING (operator-managed reconciliation).
 */
@Component
public class JpaInvoiceIdempotencyStore implements InvoiceIdempotencyStore {

    private final IdempotencyClaimJpaRepository jpa;
    private final InvoiceJpaRepository invoices;
    private final Clock clock;

    public JpaInvoiceIdempotencyStore(IdempotencyClaimJpaRepository jpa,
                                      InvoiceJpaRepository invoices,
                                      Clock clock) {
        this.jpa = jpa;
        this.invoices = invoices;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<Invoice> beginClaim(IdempotencyKey key, String requestHash) {
        Optional<IdempotencyClaimEntity> existing = jpa.findById(key.value());
        if (existing.isPresent()) {
            return resolveExisting(key, requestHash, existing.get());
        }
        // No row yet — try INSERT. A concurrent INSERT will throw on the unique PK.
        try {
            IdempotencyClaimEntity row = new IdempotencyClaimEntity();
            row.setKey(key.value());
            row.setRequestHash(requestHash);
            row.setState(IdempotencyClaimEntity.State.PENDING);
            Instant now = clock.instant();
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            jpa.saveAndFlush(row);
            return Optional.empty();
        } catch (DataIntegrityViolationException race) {
            // Lost the race — re-read and apply the same resolution path.
            IdempotencyClaimEntity winner = jpa.findById(key.value())
                .orElseThrow(() -> new IllegalStateException(
                    "claim INSERT lost the race but the winning row disappeared", race));
            return resolveExisting(key, requestHash, winner);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, Invoice invoice) {
        IdempotencyClaimEntity row = jpa.findById(key.value())
            .orElseGet(() -> {
                IdempotencyClaimEntity fresh = new IdempotencyClaimEntity();
                fresh.setKey(key.value());
                fresh.setRequestHash(requestHash);
                fresh.setCreatedAt(clock.instant());
                return fresh;
            });
        if (!row.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("completeClaim hash mismatch for key " + key.value());
        }
        if (row.getState() == IdempotencyClaimEntity.State.COMPLETED) {
            return; // idempotent
        }
        row.setState(IdempotencyClaimEntity.State.COMPLETED);
        row.setInvoiceId(invoice.id().value());
        row.setUpdatedAt(clock.instant());
        jpa.save(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        jpa.findById(key.value()).ifPresent(row -> {
            if (row.getState() == IdempotencyClaimEntity.State.PENDING
                && row.getRequestHash().equals(requestHash)) {
                jpa.delete(row);
            }
        });
    }

    private Optional<Invoice> resolveExisting(IdempotencyKey key, String requestHash,
                                              IdempotencyClaimEntity existing) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
        return switch (existing.getState()) {
            case PENDING -> throw new IdempotencyInProgressException(
                "idempotency key currently in flight: " + key.value());
            case COMPLETED -> {
                if (existing.getInvoiceId() == null) {
                    throw new IllegalStateException(
                        "completed claim has no invoice_id: " + key.value());
                }
                yield invoices.findById(existing.getInvoiceId()).map(InvoiceMapper::toDomain);
            }
        };
    }
}
