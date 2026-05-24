package com.cpayment.payment.domain.port;

import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceEvent;

import java.util.List;

/**
 * Atomic write boundary for invoice state changes. Saves the updated invoice AND any
 * domain events it produced in a SINGLE transaction so the outbox stays consistent
 * with the aggregate — the cornerstone of the transactional outbox pattern.
 *
 * <p>Implementations must reject a partial write: if the events cannot be enqueued the
 * invoice save must roll back, and vice versa.
 */
public interface InvoiceMutationGateway {

    void apply(Invoice updated, List<InvoiceEvent> events);
}
