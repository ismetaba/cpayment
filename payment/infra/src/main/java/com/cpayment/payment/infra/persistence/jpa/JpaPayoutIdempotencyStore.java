package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.port.PayoutIdempotencyStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed two-phase idempotency store for payouts. Same semantics and transaction
 * boundaries as {@link JpaInvoiceIdempotencyStore}, separate table — see
 * {@link com.cpayment.payment.domain.port.PayoutIdempotencyStore} for the rationale.
 */
@Component
public class JpaPayoutIdempotencyStore
        extends AbstractJpaIdempotencyStore<PayoutIdempotencyClaimEntity, Payout>
        implements PayoutIdempotencyStore {

    private final PayoutJpaRepository payouts;

    public JpaPayoutIdempotencyStore(PayoutIdempotencyClaimJpaRepository claims,
                                     PayoutJpaRepository payouts,
                                     Clock clock) {
        super(claims, PayoutIdempotencyClaimEntity::new, clock, "payout_id");
        this.payouts = payouts;
    }

    @Override
    protected UUID resultIdOf(Payout payout) {
        return payout.id().value();
    }

    @Override
    protected Optional<Payout> loadResult(UUID resultId) {
        return payouts.findById(resultId).map(PayoutMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<Payout> beginClaim(IdempotencyKey key, String requestHash) {
        return doBeginClaim(key, requestHash);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, Payout payout) {
        doCompleteClaim(key, requestHash, payout);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        doReleaseClaim(key, requestHash);
    }
}
