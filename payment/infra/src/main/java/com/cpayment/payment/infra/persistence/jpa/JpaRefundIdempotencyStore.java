package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.port.RefundIdempotencyStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
public class JpaRefundIdempotencyStore implements RefundIdempotencyStore {

    private final RefundIdempotencyClaimJpaRepository jpa;
    private final RefundJpaRepository refunds;
    private final Clock clock;

    public JpaRefundIdempotencyStore(RefundIdempotencyClaimJpaRepository jpa,
                                     RefundJpaRepository refunds,
                                     Clock clock) {
        this.jpa = jpa;
        this.refunds = refunds;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<Refund> beginClaim(IdempotencyKey key, String requestHash) {
        Optional<RefundIdempotencyClaimEntity> existing = jpa.findById(key.value());
        if (existing.isPresent()) return resolveExisting(key, requestHash, existing.get());
        try {
            RefundIdempotencyClaimEntity row = new RefundIdempotencyClaimEntity();
            row.setKey(key.value());
            row.setRequestHash(requestHash);
            row.setState(RefundIdempotencyClaimEntity.State.PENDING);
            Instant now = clock.instant();
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            jpa.saveAndFlush(row);
            return Optional.empty();
        } catch (DataIntegrityViolationException race) {
            RefundIdempotencyClaimEntity winner = jpa.findById(key.value())
                .orElseThrow(() -> new IllegalStateException(
                    "refund-claim INSERT lost the race but winner row disappeared", race));
            return resolveExisting(key, requestHash, winner);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, Refund refund) {
        RefundIdempotencyClaimEntity row = jpa.findById(key.value())
            .orElseGet(() -> {
                RefundIdempotencyClaimEntity fresh = new RefundIdempotencyClaimEntity();
                fresh.setKey(key.value());
                fresh.setRequestHash(requestHash);
                fresh.setCreatedAt(clock.instant());
                return fresh;
            });
        if (!row.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("completeClaim hash mismatch for key " + key.value());
        }
        if (row.getState() == RefundIdempotencyClaimEntity.State.COMPLETED) return;
        row.setState(RefundIdempotencyClaimEntity.State.COMPLETED);
        row.setRefundId(refund.id().value());
        row.setUpdatedAt(clock.instant());
        jpa.save(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        jpa.findById(key.value()).ifPresent(row -> {
            if (row.getState() == RefundIdempotencyClaimEntity.State.PENDING
                && row.getRequestHash().equals(requestHash)) {
                jpa.delete(row);
            }
        });
    }

    private Optional<Refund> resolveExisting(IdempotencyKey key, String requestHash,
                                             RefundIdempotencyClaimEntity existing) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
        return switch (existing.getState()) {
            case PENDING -> throw new IdempotencyInProgressException(
                "idempotency key currently in flight: " + key.value());
            case COMPLETED -> {
                if (existing.getRefundId() == null) {
                    throw new IllegalStateException(
                        "completed refund-claim has no refund_id: " + key.value());
                }
                yield refunds.findById(existing.getRefundId()).map(RefundMapper::toDomain);
            }
        };
    }
}
