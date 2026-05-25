package com.cpayment.payment.domain.model;

public enum RefundStatus {
    ISSUED,     // cus-server accepted; on-chain pending
    BROADCAST,  // tx in mempool / first sightings
    CONFIRMED,  // terminal success
    FAILED      // terminal failure
}
