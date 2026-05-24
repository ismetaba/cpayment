package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.port.InvoiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA-backed {@link InvoiceRepository}. Production wiring; tests of the domain use
 * cases use the in-memory adapter directly without Spring.
 *
 * <p>Each call is wrapped in a transaction so saves and reads are consistent.
 * Concurrent updates of the same invoice rely on the natural-key primary index;
 * stronger conflict detection (optimistic locking) can be layered with a
 * {@code @Version} column if/when concurrent state transitions need to be rejected.
 */
@Component
public class JpaInvoiceRepository implements InvoiceRepository {

    private final InvoiceJpaRepository jpa;

    public JpaInvoiceRepository(InvoiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(Invoice invoice) {
        jpa.save(InvoiceMapper.toEntity(invoice));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(InvoiceId id) {
        return jpa.findById(id.value()).map(InvoiceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findByCustodyAccount(AccountId account) {
        return jpa.findByCustodyAccountId(account.value()).map(InvoiceMapper::toDomain);
    }
}
