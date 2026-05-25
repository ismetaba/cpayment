package com.cpayment.payment.domain.exception;

import com.cpayment.payment.domain.model.InvoiceId;

public class InvoiceNotRefundableException extends PaymentException {

    public InvoiceNotRefundableException(InvoiceId id, String reason) {
        super("invoice " + id.value() + " not refundable: " + reason);
    }
}
