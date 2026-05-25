package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.BroadcastPayout;
import com.cpayment.payment.domain.model.CancelledPayout;
import com.cpayment.payment.domain.model.ConfirmedPayout;
import com.cpayment.payment.domain.model.FailedPayout;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutStatus;
import com.cpayment.payment.domain.model.ReplacedPayout;
import com.cpayment.payment.domain.model.SubmittedPayout;

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
    Integer confirmations,
    BigInteger feeActual,
    String feeAsset,
    String failureReason,
    String failureMessage,
    UUID replacedBy,
    Instant createdAt
) {

    public static PayoutResponse from(Payout p) {
        String txHash = null;
        Integer confirmations = null;
        BigInteger feeActual = null;
        String feeAsset = null;
        String failureReason = null;
        String failureMessage = null;
        UUID replacedBy = null;

        switch (p) {
            case SubmittedPayout s -> { /* nothing extra */ }
            case BroadcastPayout b -> { txHash = b.txHash(); }
            case ConfirmedPayout c -> {
                txHash = c.txHash();
                confirmations = c.confirmations();
                feeActual = c.feeActual();
                feeAsset = c.feeAsset().canonical();
            }
            case FailedPayout f -> {
                txHash = f.txHash().orElse(null);
                failureReason = f.failureReason().name();
                failureMessage = f.failureMessage();
            }
            case ReplacedPayout r -> { replacedBy = r.replacedBy().value(); }
            case CancelledPayout x -> { /* nothing extra */ }
        }

        return new PayoutResponse(
            p.id().value(),
            p.merchantId().value(),
            p.asset().canonical(),
            p.fromAddress(),
            p.toAddress(),
            p.amount(),
            p.memo().orElse(null),
            p.status(),
            p.custodyTransferId().value(),
            txHash, confirmations, feeActual, feeAsset,
            failureReason, failureMessage, replacedBy,
            p.createdAt()
        );
    }
}
