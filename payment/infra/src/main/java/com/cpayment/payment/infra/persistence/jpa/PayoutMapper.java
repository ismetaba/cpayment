package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutId;

import java.util.Optional;

final class PayoutMapper {

    private PayoutMapper() {}

    static PayoutEntity toEntity(Payout p) {
        PayoutEntity e = new PayoutEntity();
        e.setId(p.id().value());
        e.setMerchantId(p.merchantId().value());
        e.setAssetCanonical(p.asset().canonical());
        e.setFromAddress(p.fromAddress());
        e.setToAddress(p.toAddress());
        e.setAmount(p.amount());
        e.setMemo(p.memo().orElse(null));
        e.setStatus(p.status());
        e.setCustodyTransferId(p.custodyTransferId().map(TransferId::value).orElse(null));
        e.setTxHash(p.txHash().orElse(null));
        e.setConfirmations(p.confirmations().orElse(null));
        e.setFeeActual(p.feeActual().orElse(null));
        e.setFeeAssetCanonical(p.feeAsset().map(AssetId::canonical).orElse(null));
        e.setFailureReason(p.failureReason().map(Enum::name).orElse(null));
        e.setFailureMessage(p.failureMessage().orElse(null));
        e.setReplacedBy(p.replacedBy().map(TransferId::value).orElse(null));
        e.setCreatedAt(p.createdAt());
        e.setUpdatedAt(p.updatedAt());
        return e;
    }

    static Payout toDomain(PayoutEntity e) {
        return new Payout(
            PayoutId.of(e.getId()),
            MerchantId.of(e.getMerchantId()),
            AssetId.parse(e.getAssetCanonical()),
            e.getFromAddress(),
            e.getToAddress(),
            e.getAmount(),
            Optional.ofNullable(e.getMemo()),
            e.getStatus(),
            Optional.ofNullable(e.getCustodyTransferId()).map(TransferId::of),
            Optional.ofNullable(e.getTxHash()),
            Optional.ofNullable(e.getConfirmations()),
            Optional.ofNullable(e.getFeeActual()),
            Optional.ofNullable(e.getFeeAssetCanonical()).map(AssetId::parse),
            Optional.ofNullable(e.getFailureReason()).map(FailureReason::valueOf),
            Optional.ofNullable(e.getFailureMessage()),
            Optional.ofNullable(e.getReplacedBy()).map(TransferId::of),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
