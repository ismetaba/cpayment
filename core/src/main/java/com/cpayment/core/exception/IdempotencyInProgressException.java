package com.cpayment.core.exception;

/**
 * Thrown when the caller's idempotency key matches an in-flight (PENDING) claim — either
 * another concurrent request is processing the same operation, or a previous attempt
 * crashed after the side-effecting work but before completion was recorded.
 *
 * <p>Distinct from {@link IdempotencyConflictException}: this one is a transient state
 * (the original request may still finish, or operator intervention may be needed).
 * Maps to HTTP 409 with code {@code IDEMPOTENCY_IN_PROGRESS} so clients can distinguish
 * "your retry collided with the in-flight original" from "you reused a key with a
 * different body".
 */
public class IdempotencyInProgressException extends CpaymentException {
    public IdempotencyInProgressException(String message) { super(message); }
}
