package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.MerchantId;

import java.util.Optional;

/**
 * Pure mapping between the JPA entity and the immutable domain record. Stateless;
 * no Spring bean — used directly by the JPA adapter.
 */
final class InvoiceMapper {

    private InvoiceMapper() {}

    static InvoiceEntity toEntity(Invoice i) {
        InvoiceEntity e = new InvoiceEntity();
        e.setId(i.id().value());
        e.setMerchantId(i.merchantId().value());
        e.setAssetCanonical(i.asset().canonical());
        e.setExpectedAmount(i.expectedAmount());
        e.setMinConfirmations(i.minConfirmations());
        e.setStatus(i.status());
        e.setCustodyAccountId(i.custodyAccount().value());
        e.setDepositAddress(i.depositAddress());
        e.setReceivedTxHash(i.receivedTxHash().orElse(null));
        e.setReceivedAmount(i.receivedAmount().orElse(null));
        e.setReceivedConfirmations(i.receivedConfirmations().orElse(null));
        e.setCreatedAt(i.createdAt());
        e.setUpdatedAt(i.updatedAt());
        return e;
    }

    static Invoice toDomain(InvoiceEntity e) {
        return new Invoice(
            InvoiceId.of(e.getId()),
            MerchantId.of(e.getMerchantId()),
            AssetId.parse(e.getAssetCanonical()),
            e.getExpectedAmount(),
            e.getMinConfirmations(),
            e.getStatus(),
            AccountId.of(e.getCustodyAccountId()),
            e.getDepositAddress(),
            Optional.ofNullable(e.getReceivedTxHash()),
            Optional.ofNullable(e.getReceivedAmount()),
            Optional.ofNullable(e.getReceivedConfirmations()),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
