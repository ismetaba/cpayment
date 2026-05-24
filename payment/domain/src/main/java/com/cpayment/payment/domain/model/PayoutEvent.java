package com.cpayment.payment.domain.model;

public record PayoutEvent(PayoutEventType type, Payout payout) {

    public static PayoutEvent of(PayoutEventType type, Payout payout) {
        return new PayoutEvent(type, payout);
    }
}
