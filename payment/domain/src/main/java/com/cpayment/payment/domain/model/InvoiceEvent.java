package com.cpayment.payment.domain.model;

/**
 * A domain-level event tied to an invoice state transition. Use cases enumerate which
 * events to emit; the mutation gateway persists them transactionally alongside the
 * invoice update. A separate dispatcher delivers them as merchant webhooks.
 */
public record InvoiceEvent(InvoiceEventType type, Invoice invoice) {

    public static InvoiceEvent of(InvoiceEventType type, Invoice invoice) {
        return new InvoiceEvent(type, invoice);
    }
}
