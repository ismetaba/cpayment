package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed two-phase idempotency store for invoices. All claim-protocol logic lives in
 * {@link AbstractJpaIdempotencyStore}; this class binds it to the invoice resource.
 *
 * <h2>Transaction boundaries</h2>
 * <p>{@code beginClaim} runs as {@code REQUIRES_NEW READ_COMMITTED} — independent of any
 * outer transaction. The PENDING row is durable as soon as we return; if the caller's
 * outer transaction later rolls back its side effects, the claim still stands and a retry
 * sees PENDING (operator-managed reconciliation).
 */
@Component
public class JpaInvoiceIdempotencyStore
        extends AbstractJpaIdempotencyStore<IdempotencyClaimEntity, Invoice>
        implements InvoiceIdempotencyStore {

    private final InvoiceJpaRepository invoices;

    public JpaInvoiceIdempotencyStore(IdempotencyClaimJpaRepository claims,
                                      InvoiceJpaRepository invoices,
                                      Clock clock) {
        super(claims, IdempotencyClaimEntity::new, clock, "invoice_id");
        this.invoices = invoices;
    }

    @Override
    protected UUID resultIdOf(Invoice invoice) {
        return invoice.id().value();
    }

    @Override
    protected Optional<Invoice> loadResult(UUID resultId) {
        return invoices.findById(resultId).map(InvoiceMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<Invoice> beginClaim(IdempotencyKey key, String requestHash) {
        return doBeginClaim(key, requestHash);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeClaim(IdempotencyKey key, String requestHash, Invoice invoice) {
        doCompleteClaim(key, requestHash, invoice);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(IdempotencyKey key, String requestHash) {
        doReleaseClaim(key, requestHash);
    }
}
