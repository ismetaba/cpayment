package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.exception.InvoiceNotFoundException;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.port.InvoiceRepository;

import java.util.Objects;

/**
 * Read-only query — surfaces the invoice or raises a typed not-found exception that
 * the web layer maps to 404. Kept as a use case (not a direct repository call from
 * the controller) so it stays mockable, future-extendable (auth, tenant scope, audit),
 * and aligned with the rest of the application service layer.
 */
public final class FindInvoiceUseCase {

    private final InvoiceRepository invoices;

    public FindInvoiceUseCase(InvoiceRepository invoices) {
        this.invoices = Objects.requireNonNull(invoices, "invoices");
    }

    public Invoice byId(InvoiceId id) {
        Objects.requireNonNull(id, "id");
        return invoices.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }
}
