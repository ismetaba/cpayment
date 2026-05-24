package com.cpayment.custody.domain.exception;

import com.cpayment.core.exception.CpaymentException;

public abstract class CustodyException extends CpaymentException {
    protected CustodyException(String message) { super(message); }
    protected CustodyException(String message, Throwable cause) { super(message, cause); }
}
