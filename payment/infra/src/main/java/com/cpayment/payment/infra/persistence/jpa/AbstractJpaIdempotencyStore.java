package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.core.model.IdempotencyKey;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared two-phase idempotency-claim logic for the JPA stores. The {@code key} column is
 * the primary key, so concurrent INSERTs collide at the database boundary; the collision
 * is translated into the correct domain exception by re-reading the surviving row.
 *
 * <p>Subclasses are thin: they implement the typed {@code *IdempotencyStore} port, carry
 * the {@code @Transactional} boundaries on their own public methods (so Spring's proxy
 * applies cleanly), and supply only the two type-specific operations — how to read the
 * result id off a domain object and how to load a domain object back from a result id.
 *
 * @param <E> the claim entity type
 * @param <D> the completed-resource domain type returned to callers
 */
public abstract class AbstractJpaIdempotencyStore<E extends AbstractIdempotencyClaimEntity, D> {

    private final JpaRepository<E, String> claims;
    private final Supplier<E> entityFactory;
    private final Clock clock;
    private final String resultColumn;

    /**
     * @param resultColumn human-readable result-column name, used only in the
     *                     "completed claim has no result id" diagnostic.
     */
    protected AbstractJpaIdempotencyStore(JpaRepository<E, String> claims,
                                          Supplier<E> entityFactory,
                                          Clock clock,
                                          String resultColumn) {
        this.claims = claims;
        this.entityFactory = entityFactory;
        this.clock = clock;
        this.resultColumn = resultColumn;
    }

    /** Extract the persistable result id from a completed domain object. */
    protected abstract UUID resultIdOf(D result);

    /** Load the domain object for a completed claim's result id. */
    protected abstract Optional<D> loadResult(UUID resultId);

    protected final Optional<D> doBeginClaim(IdempotencyKey key, String requestHash) {
        Optional<E> existing = claims.findById(key.value());
        if (existing.isPresent()) {
            return resolveExisting(key, requestHash, existing.get());
        }
        // No row yet — try INSERT. A concurrent INSERT will throw on the unique PK.
        try {
            E row = entityFactory.get();
            row.setKey(key.value());
            row.setRequestHash(requestHash);
            row.setState(ClaimState.PENDING);
            Instant now = clock.instant();
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            claims.saveAndFlush(row);
            return Optional.empty();
        } catch (DataIntegrityViolationException race) {
            // Lost the race — re-read and apply the same resolution path.
            E winner = claims.findById(key.value())
                .orElseThrow(() -> new IllegalStateException(
                    "idempotency claim INSERT lost the race but the winning row disappeared", race));
            return resolveExisting(key, requestHash, winner);
        }
    }

    protected final void doCompleteClaim(IdempotencyKey key, String requestHash, D result) {
        E row = claims.findById(key.value())
            .orElseGet(() -> {
                E fresh = entityFactory.get();
                fresh.setKey(key.value());
                fresh.setRequestHash(requestHash);
                fresh.setCreatedAt(clock.instant());
                return fresh;
            });
        if (!row.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("completeClaim hash mismatch for key " + key.value());
        }
        if (row.getState() == ClaimState.COMPLETED) {
            return; // idempotent
        }
        row.setState(ClaimState.COMPLETED);
        row.setResultId(resultIdOf(result));
        row.setUpdatedAt(clock.instant());
        claims.save(row);
    }

    protected final void doReleaseClaim(IdempotencyKey key, String requestHash) {
        claims.findById(key.value()).ifPresent(row -> {
            if (row.getState() == ClaimState.PENDING
                && row.getRequestHash().equals(requestHash)) {
                claims.delete(row);
            }
        });
    }

    private Optional<D> resolveExisting(IdempotencyKey key, String requestHash, E existing) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                "idempotency key reused with a different request payload: " + key.value());
        }
        return switch (existing.getState()) {
            case PENDING -> throw new IdempotencyInProgressException(
                "idempotency key currently in flight: " + key.value());
            case COMPLETED -> {
                if (existing.getResultId() == null) {
                    throw new IllegalStateException(
                        "completed claim has no " + resultColumn + ": " + key.value());
                }
                yield loadResult(existing.getResultId());
            }
        };
    }
}
