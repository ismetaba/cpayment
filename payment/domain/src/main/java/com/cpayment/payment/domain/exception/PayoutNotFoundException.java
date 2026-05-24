package com.cpayment.payment.domain.exception;

import com.cpayment.payment.domain.model.PayoutId;

public class PayoutNotFoundException extends PaymentException {
    public PayoutNotFoundException(PayoutId id) {
        super("payout not found: " + id.value());
    }
}
