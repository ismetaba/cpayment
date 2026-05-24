package com.cpayment.payment.domain.model;

/** Webhook event types. Stable wire names — clients depend on these strings. */
public enum InvoiceEventType {
    INVOICE_CREATED,
    INVOICE_DETECTED,
    INVOICE_PAID,
    INVOICE_UNDERPAID,
    INVOICE_EXPIRED,
    INVOICE_CANCELLED
}
