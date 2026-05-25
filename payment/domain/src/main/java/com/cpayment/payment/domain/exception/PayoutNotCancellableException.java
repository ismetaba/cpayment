package com.cpayment.payment.domain.exception;

import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.model.PayoutStatus;

public class PayoutNotCancellableException extends PaymentException {

    public PayoutNotCancellableException(PayoutId id, PayoutStatus current) {
        super("payout " + id.value() + " cannot be cancelled in status " + current);
    }
}
