package com.cpayment.payment.infra.persistence;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.port.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Placeholder repository. Production replacement: JPA + Oracle table {@code cpayment_invoice}
 * with an index on {@code custody_account_id}.
 *
 * <p>Two maps mirror each other to give O(1) lookups for both access patterns. Writes
 * use a single critical section to keep them consistent.
 */
@Component
public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final ConcurrentMap<InvoiceId, Invoice> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<AccountId, InvoiceId> byCustodyAccount = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(Invoice invoice) {
        byId.put(invoice.id(), invoice);
        byCustodyAccount.put(invoice.custodyAccount(), invoice.id());
    }

    @Override
    public Optional<Invoice> findById(InvoiceId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Invoice> findByCustodyAccount(AccountId account) {
        InvoiceId id = byCustodyAccount.get(account);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }
}
