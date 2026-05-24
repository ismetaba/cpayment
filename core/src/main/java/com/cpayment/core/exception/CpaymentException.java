package com.cpayment.core.exception;

public abstract class CpaymentException extends RuntimeException {
    protected CpaymentException(String message) { super(message); }
    protected CpaymentException(String message, Throwable cause) { super(message, cause); }
}
