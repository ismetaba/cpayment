package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.port.PayoutIdempotencyStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Mirror of {@code JpaInvoiceIdempotencyStore} for payouts. Same semantics, separate
 * table — see {@link com.cpayment.payment.domain.port.PayoutIdempotencyStore} class doc
 * for the rationale.
 */
@Component
public class JpaPayoutIdempotencyStore implements PayoutIdempotencyStore {

    private final PayoutIdempotencyClaimJpaRepository jpa;
    private final PayoutJpaRepository payouts;
    private final Clock clock;

    public JpaPayoutIdempotencyStore(PayoutIdempotencyClaimJpaRepository jpa,
                                     PayoutJpaRepository payouts,
                                     Clock clock) {
        this.jpa = jpa;
        this.payouts = payouts;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<Payout> beginClaim(IdempotencyKey key, String requestHash) {
        Optional<PayoutIdempotencyClaimEntity> existing = jpa.findById(key.value());
        if (existing.isPresent()) {
            return resolveExisting(key, requestHash, existing.get());
        }
        try {
            PayoutIdempotencyClaimEntity row = new PayoutIdempotencyClaimEntity();
            row.setKey(key.value());
            row.setRequestHash(requestHash);
            row.setState(PayoutIdempotencyClaimEntity.State.PENDING);
            Instant now = clock.instant();
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            jpa.saveAndFlush(row);
            return Optional.empty();
        } catch (DataIntegrityViolationException race) {
            PayoutIdempotencyClaimEntity winner = jpa.findById(key.value())
                .orElseThrow(() -> new IllegalStateException(
                    "payout-claim INSERT lost the race but the winning row disappeared", race));
            return resolveExisting(key, requestHash, winner);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, Payout payout) {
        PayoutIdempotencyClaimEntity row = jpa.findById(key.value())
            .orElseGet(() -> {
                PayoutIdempotencyClaimEntity fresh = new PayoutIdempotencyClaimEntity();
                fresh.setKey(key.value());
                fresh.setRequestHash(requestHash);
                fresh.setCreatedAt(clock.instant());
                return fresh;
            });
        if (!row.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("completeClaim hash mismatch for key " + key.value());
        }
        if (row.getState() == PayoutIdempotencyClaimEntity.State.COMPLETED) return;
        row.setState(PayoutIdempotencyClaimEntity.State.COMPLETED);
        row.setPayoutId(payout.id().value());
        row.setUpdatedAt(clock.instant());
        jpa.save(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        jpa.findById(key.value()).ifPresent(row -> {
            if (row.getState() == PayoutIdempotencyClaimEntity.State.PENDING
                && row.getRequestHash().equals(requestHash)) {
                jpa.delete(row);
            }
        });
    }

    private Optional<Payout> resolveExisting(IdempotencyKey key, String requestHash,
                                             PayoutIdempotencyClaimEntity existing) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
        return switch (existing.getState()) {
            case PENDING -> throw new IdempotencyInProgressException(
                "idempotency key currently in flight: " + key.value());
            case COMPLETED -> {
                if (existing.getPayoutId() == null) {
                    throw new IllegalStateException(
                        "completed payout-claim has no payout_id: " + key.value());
                }
                yield payouts.findById(existing.getPayoutId()).map(PayoutMapper::toDomain);
            }
        };
    }
}
