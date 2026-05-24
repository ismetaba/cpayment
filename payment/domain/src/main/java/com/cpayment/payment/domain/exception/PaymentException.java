package com.cpayment.payment.domain.exception;

import com.cpayment.core.exception.CpaymentException;

public abstract class PaymentException extends CpaymentException {
    protected PaymentException(String message) { super(message); }
    protected PaymentException(String message, Throwable cause) { super(message, cause); }
}
