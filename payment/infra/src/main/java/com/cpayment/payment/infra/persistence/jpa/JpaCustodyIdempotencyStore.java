package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferIdempotencyStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Persistent {@link TransferIdempotencyStore}. Mirrors the invoice / payout / refund
 * stores: REQUIRES_NEW transactions so the claim survives a rollback in the calling
 * use case, and a unique-key insert race is funnelled to the existing-row read path.
 *
 * <p>Closes the regression hole identified in code review: the previous in-memory
 * store lost dedupe state on every restart, so a process-crash + retry of a
 * gas-funder top-up (or any direct-to-adapter caller) would re-fire the cus-server
 * POST and double-send.
 */
@Component
public class JpaCustodyIdempotencyStore implements TransferIdempotencyStore {

    private final CustodyIdempotencyClaimJpaRepository jpa;
    private final Clock clock;

    public JpaCustodyIdempotencyStore(CustodyIdempotencyClaimJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<TransferId> beginClaim(IdempotencyKey key, String requestHash) {
        Optional<CustodyIdempotencyClaimEntity> existing = jpa.findById(key.value());
        if (existing.isPresent()) return resolveExisting(key, requestHash, existing.get());
        try {
            CustodyIdempotencyClaimEntity row = new CustodyIdempotencyClaimEntity();
            row.setKey(key.value());
            row.setRequestHash(requestHash);
            row.setState(CustodyIdempotencyClaimEntity.State.PENDING);
            Instant now = clock.instant();
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            jpa.saveAndFlush(row);
            return Optional.empty();
        } catch (DataIntegrityViolationException race) {
            CustodyIdempotencyClaimEntity winner = jpa.findById(key.value())
                .orElseThrow(() -> new IllegalStateException(
                    "custody-claim INSERT lost the race but winner row disappeared", race));
            return resolveExisting(key, requestHash, winner);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, TransferId transferId) {
        CustodyIdempotencyClaimEntity row = jpa.findById(key.value())
            .orElseGet(() -> {
                CustodyIdempotencyClaimEntity fresh = new CustodyIdempotencyClaimEntity();
                fresh.setKey(key.value());
                fresh.setRequestHash(requestHash);
                fresh.setCreatedAt(clock.instant());
                return fresh;
            });
        if (!row.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                "completeClaim hash mismatch for key " + key.value());
        }
        if (row.getState() == CustodyIdempotencyClaimEntity.State.COMPLETED) return;
        row.setState(CustodyIdempotencyClaimEntity.State.COMPLETED);
        row.setTransferId(transferId.value());
        row.setUpdatedAt(clock.instant());
        jpa.save(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        jpa.findById(key.value()).ifPresent(row -> {
            if (row.getState() == CustodyIdempotencyClaimEntity.State.PENDING
                && row.getRequestHash().equals(requestHash)) {
                jpa.delete(row);
            }
        });
    }

    private Optional<TransferId> resolveExisting(IdempotencyKey key, String requestHash,
                                                 CustodyIdempotencyClaimEntity existing) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
        return switch (existing.getState()) {
            case PENDING -> throw new IdempotencyInProgressException(
                "idempotency key currently in flight: " + key.value());
            case COMPLETED -> {
                if (existing.getTransferId() == null) {
                    throw new IllegalStateException(
                        "completed custody-claim has no transfer_id: " + key.value());
                }
                yield Optional.of(TransferId.of(existing.getTransferId()));
            }
        };
    }
}
