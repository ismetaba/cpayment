package com.cpayment.payment.domain.exception;

import com.cpayment.payment.domain.model.RefundId;

public class RefundNotFoundException extends PaymentException {
    public RefundNotFoundException(RefundId id) {
        super("refund not found: " + id.value());
    }
}
