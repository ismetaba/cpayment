package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.BroadcastRefund;
import com.cpayment.payment.domain.model.ConfirmedRefund;
import com.cpayment.payment.domain.model.FailedRefund;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.IssuedRefund;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundId;
import com.cpayment.payment.domain.model.RefundStatus;

import java.util.Optional;

final class RefundMapper {

    private RefundMapper() {}

    static RefundEntity toEntity(Refund refund) {
        RefundEntity e = baseEntity(refund);

        switch (refund) {
            case IssuedRefund r -> {
                e.setStatus(RefundStatus.ISSUED);
            }
            case BroadcastRefund r -> {
                e.setStatus(RefundStatus.BROADCAST);
                e.setTxHash(r.txHash());
            }
            case ConfirmedRefund r -> {
                e.setStatus(RefundStatus.CONFIRMED);
                e.setTxHash(r.txHash());
                e.setConfirmations(r.confirmations());
                e.setFeeActual(r.feeActual());
                e.setFeeAssetCanonical(r.feeAsset().canonical());
            }
            case FailedRefund r -> {
                e.setStatus(RefundStatus.FAILED);
                r.txHash().ifPresent(e::setTxHash);
                e.setFailureReason(r.failureReason().name());
                e.setFailureMessage(r.failureMessage());
            }
        }
        return e;
    }

    static Refund toDomain(RefundEntity e) {
        RefundId id = RefundId.of(e.getId());
        InvoiceId invoiceId = InvoiceId.of(e.getInvoiceId());
        MerchantId merchantId = MerchantId.of(e.getMerchantId());
        AssetId asset = AssetId.parse(e.getAssetCanonical());
        Optional<String> memo = Optional.ofNullable(e.getMemo());
        TransferId transferId = TransferId.of(requireNonNull(e.getCustodyTransferId(), "custodyTransferId", e));

        return switch (e.getStatus()) {
            case ISSUED -> new IssuedRefund(id, invoiceId, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), e.getReason(), memo,
                transferId, e.getCreatedAt(), e.getUpdatedAt());

            case BROADCAST -> new BroadcastRefund(id, invoiceId, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), e.getReason(), memo,
                transferId, requireNonBlank(e.getTxHash(), "txHash", e),
                e.getCreatedAt(), e.getUpdatedAt());

            case CONFIRMED -> new ConfirmedRefund(id, invoiceId, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), e.getReason(), memo,
                transferId, requireNonBlank(e.getTxHash(), "txHash", e),
                requireNonNull(e.getConfirmations(), "confirmations", e),
                requireNonNull(e.getFeeActual(), "feeActual", e),
                AssetId.parse(requireNonBlank(e.getFeeAssetCanonical(), "feeAsset", e)),
                e.getCreatedAt(), e.getUpdatedAt());

            case FAILED -> new FailedRefund(id, invoiceId, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), e.getReason(), memo,
                transferId, Optional.ofNullable(e.getTxHash()),
                FailureReason.valueOf(requireNonBlank(e.getFailureReason(), "failureReason", e)),
                e.getFailureMessage() != null ? e.getFailureMessage() : "(none)",
                e.getCreatedAt(), e.getUpdatedAt());
        };
    }

    private static RefundEntity baseEntity(Refund r) {
        RefundEntity e = new RefundEntity();
        e.setId(r.id().value());
        e.setInvoiceId(r.invoiceId().value());
        e.setMerchantId(r.merchantId().value());
        e.setAssetCanonical(r.asset().canonical());
        e.setFromAddress(r.fromAddress());
        e.setToAddress(r.toAddress());
        e.setAmount(r.amount());
        e.setReason(r.reason());
        e.setMemo(r.memo().orElse(null));
        e.setCustodyTransferId(r.custodyTransferId().value());
        e.setCreatedAt(r.createdAt());
        e.setUpdatedAt(r.updatedAt());
        return e;
    }

    private static <T> T requireNonNull(T value, String field, RefundEntity e) {
        if (value == null) {
            throw new IllegalStateException(
                "RefundEntity " + e.getId() + " status " + e.getStatus()
                    + " is missing required field " + field);
        }
        return value;
    }

    private static String requireNonBlank(String value, String field, RefundEntity e) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "RefundEntity " + e.getId() + " status " + e.getStatus()
                    + " is missing required field " + field);
        }
        return value;
    }
}
