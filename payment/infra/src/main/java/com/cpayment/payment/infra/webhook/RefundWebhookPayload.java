package com.cpayment.payment.infra.webhook;

import com.cpayment.payment.domain.model.BroadcastRefund;
import com.cpayment.payment.domain.model.ConfirmedRefund;
import com.cpayment.payment.domain.model.FailedRefund;
import com.cpayment.payment.domain.model.IssuedRefund;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundEvent;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record RefundWebhookPayload(
    UUID eventId,
    String eventType,
    Instant createdAt,
    RefundBody refund
) {

    public static RefundWebhookPayload of(UUID eventId, RefundEvent e) {
        return new RefundWebhookPayload(eventId, e.type().name(), Instant.now(), bodyOf(e.refund()));
    }

    private static RefundBody bodyOf(Refund r) {
        String txHash = null;
        Integer confirmations = null;
        BigInteger feeActual = null;
        String feeAsset = null;
        String failureReason = null;
        String failureMessage = null;

        switch (r) {
            case IssuedRefund x -> { /* nothing extra */ }
            case BroadcastRefund x -> { txHash = x.txHash(); }
            case ConfirmedRefund x -> {
                txHash = x.txHash();
                confirmations = x.confirmations();
                feeActual = x.feeActual();
                feeAsset = x.feeAsset().canonical();
            }
            case FailedRefund x -> {
                txHash = x.txHash().orElse(null);
                failureReason = x.failureReason().name();
                failureMessage = x.failureMessage();
            }
        }

        return new RefundBody(
            r.id().value(),
            r.invoiceId().value(),
            r.merchantId().value(),
            r.asset().canonical(),
            r.fromAddress(),
            r.toAddress(),
            r.amount(),
            r.reason().name(),
            r.memo().orElse(null),
            r.status().name(),
            r.custodyTransferId().value(),
            txHash, confirmations, feeActual, feeAsset,
            failureReason, failureMessage
        );
    }

    public record RefundBody(
        UUID id,
        UUID invoiceId,
        UUID merchantId,
        String asset,
        String fromAddress,
        String toAddress,
        BigInteger amount,
        String reason,
        String memo,
        String status,
        UUID transferId,
        String txHash,
        Integer confirmations,
        BigInteger feeActual,
        String feeAsset,
        String failureReason,
        String failureMessage
    ) {}
}
