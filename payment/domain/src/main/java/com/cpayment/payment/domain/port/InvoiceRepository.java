package com.cpayment.payment.domain.port;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;

import java.util.Optional;

/**
 * Outbound port — invoice persistence.
 *
 * <p>Implementations must be:
 * <ul>
 *   <li>thread-safe under concurrent {@link #save} calls for the same invoice id
 *       (last writer wins is acceptable; conflict detection lives upstream);</li>
 *   <li>read-your-writes consistent within a single process.</li>
 * </ul>
 */
public interface InvoiceRepository {

    void save(Invoice invoice);

    Optional<Invoice> findById(InvoiceId id);

    /**
     * Locate the invoice that owns the given custody deposit account.
     * Used by the deposit event handler to correlate an inbound payment with an invoice.
     */
    Optional<Invoice> findByCustodyAccount(AccountId account);
}
