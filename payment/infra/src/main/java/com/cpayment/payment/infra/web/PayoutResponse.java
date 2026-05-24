package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutStatus;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
    UUID id,
    UUID merchantId,
    String asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    String memo,
    PayoutStatus status,
    UUID transferId,
    String txHash,
    Instant createdAt
) {

    public static PayoutResponse from(Payout p) {
        return new PayoutResponse(
            p.id().value(),
            p.merchantId().value(),
            p.asset().canonical(),
            p.fromAddress(),
            p.toAddress(),
            p.amount(),
            p.memo().orElse(null),
            p.status(),
            p.custodyTransferId().map(t -> t.value()).orElse(null),
            p.txHash().orElse(null),
            p.createdAt()
        );
    }
}
