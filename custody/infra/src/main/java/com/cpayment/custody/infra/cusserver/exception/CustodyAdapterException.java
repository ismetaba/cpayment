package com.cpayment.custody.infra.cusserver.exception;

import com.cpayment.custody.domain.exception.CustodyException;

/**
 * Thrown when the cus-server adapter cannot complete a call (network error,
 * unexpected response shape, mapping failure). Distinct from domain-level
 * {@code TransferRejectedException} which represents a business decision.
 */
public class CustodyAdapterException extends CustodyException {
    public CustodyAdapterException(String message) { super(message); }
    public CustodyAdapterException(String message, Throwable cause) { super(message, cause); }
}
