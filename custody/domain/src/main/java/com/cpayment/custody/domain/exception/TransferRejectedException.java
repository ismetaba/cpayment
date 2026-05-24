package com.cpayment.custody.domain.exception;

import com.cpayment.custody.domain.event.FailureReason;

public class TransferRejectedException extends CustodyException {

    private final FailureReason reason;

    public TransferRejectedException(FailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FailureReason reason() { return reason; }
}
