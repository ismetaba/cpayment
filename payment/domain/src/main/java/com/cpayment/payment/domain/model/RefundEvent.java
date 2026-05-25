package com.cpayment.payment.domain.model;

public record RefundEvent(RefundEventType type, Refund refund) {

    public static RefundEvent of(RefundEventType type, Refund refund) {
        return new RefundEvent(type, refund);
    }
}
