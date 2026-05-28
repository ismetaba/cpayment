package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferIdempotencyStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent {@link TransferIdempotencyStore}. Mirrors the invoice / payout / refund
 * stores, reusing {@link AbstractJpaIdempotencyStore}; the only difference is that the
 * result id <em>is</em> the domain value (a {@link TransferId}), so no result-repository
 * lookup is needed.
 *
 * <p>Closes the regression hole identified in code review: the previous in-memory store
 * lost dedupe state on every restart, so a process-crash + retry of a gas-funder top-up
 * (or any direct-to-adapter caller) would re-fire the cus-server POST and double-send.
 */
@Component
public class JpaCustodyIdempotencyStore
        extends AbstractJpaIdempotencyStore<CustodyIdempotencyClaimEntity, TransferId>
        implements TransferIdempotencyStore {

    public JpaCustodyIdempotencyStore(CustodyIdempotencyClaimJpaRepository claims, Clock clock) {
        super(claims, CustodyIdempotencyClaimEntity::new, clock, "transfer_id");
    }

    @Override
    protected UUID resultIdOf(TransferId transferId) {
        return transferId.value();
    }

    @Override
    protected Optional<TransferId> loadResult(UUID resultId) {
        return Optional.of(TransferId.of(resultId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<TransferId> beginClaim(IdempotencyKey key, String requestHash) {
        return doBeginClaim(key, requestHash);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, TransferId transferId) {
        doCompleteClaim(key, requestHash, transferId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        doReleaseClaim(key, requestHash);
    }
}
