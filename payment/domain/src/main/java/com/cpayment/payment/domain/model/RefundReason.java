package com.cpayment.payment.domain.model;

public enum RefundReason {
    REQUESTED_BY_CUSTOMER,
    DUPLICATE,
    FRAUDULENT,
    OVERPAYMENT,
    OTHER
}
