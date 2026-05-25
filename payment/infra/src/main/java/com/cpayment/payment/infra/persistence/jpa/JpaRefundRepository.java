package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundId;
import com.cpayment.payment.domain.model.RefundStatus;
import com.cpayment.payment.domain.port.RefundRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Component
public class JpaRefundRepository implements RefundRepository {

    private final RefundJpaRepository jpa;

    public JpaRefundRepository(RefundJpaRepository jpa) { this.jpa = jpa; }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> findById(RefundId id) {
        return jpa.findById(id.value()).map(RefundMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> findByCustodyTransferId(TransferId transferId) {
        return jpa.findByCustodyTransferId(transferId.value()).map(RefundMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> findByInvoice(InvoiceId invoiceId) {
        return jpa.findByInvoiceId(invoiceId.value()).stream().map(RefundMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigInteger sumIssuedNonFailed(InvoiceId invoiceId) {
        BigInteger v = jpa.sumNonFailedAmounts(invoiceId.value(), RefundStatus.FAILED);
        return v != null ? v : BigInteger.ZERO;
    }
}
