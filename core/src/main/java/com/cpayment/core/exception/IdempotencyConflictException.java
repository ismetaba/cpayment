package com.cpayment.core.exception;

/**
 * Thrown when an idempotency key is reused with a different request payload —
 * a clear client error indicating accidental key collision.
 *
 * <p>Lives in {@code core} so both custody and payment bounded contexts can raise
 * the same semantic exception; a single global handler maps it to {@code 409 Conflict}.
 */
public class IdempotencyConflictException extends CpaymentException {
    public IdempotencyConflictException(String message) { super(message); }
}
