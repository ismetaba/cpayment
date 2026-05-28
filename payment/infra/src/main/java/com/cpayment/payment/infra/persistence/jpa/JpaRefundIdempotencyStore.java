package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.port.RefundIdempotencyStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed two-phase idempotency store for refunds. Same semantics and transaction
 * boundaries as {@link JpaInvoiceIdempotencyStore}, separate table.
 */
@Component
public class JpaRefundIdempotencyStore
        extends AbstractJpaIdempotencyStore<RefundIdempotencyClaimEntity, Refund>
        implements RefundIdempotencyStore {

    private final RefundJpaRepository refunds;

    public JpaRefundIdempotencyStore(RefundIdempotencyClaimJpaRepository claims,
                                     RefundJpaRepository refunds,
                                     Clock clock) {
        super(claims, RefundIdempotencyClaimEntity::new, clock, "refund_id");
        this.refunds = refunds;
    }

    @Override
    protected UUID resultIdOf(Refund refund) {
        return refund.id().value();
    }

    @Override
    protected Optional<Refund> loadResult(UUID resultId) {
        return refunds.findById(resultId).map(RefundMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<Refund> beginClaim(IdempotencyKey key, String requestHash) {
        return doBeginClaim(key, requestHash);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, Refund refund) {
        doCompleteClaim(key, requestHash, refund);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        doReleaseClaim(key, requestHash);
    }
}
