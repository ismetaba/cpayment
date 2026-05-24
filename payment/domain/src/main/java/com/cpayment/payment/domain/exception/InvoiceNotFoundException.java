package com.cpayment.payment.domain.exception;

import com.cpayment.payment.domain.model.InvoiceId;

public class InvoiceNotFoundException extends PaymentException {
    public InvoiceNotFoundException(InvoiceId id) {
        super("invoice not found: " + id.value());
    }
}
