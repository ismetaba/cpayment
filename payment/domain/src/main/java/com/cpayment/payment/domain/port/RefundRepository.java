package com.cpayment.payment.domain.port;

import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundId;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface RefundRepository {

    Optional<Refund> findById(RefundId id);

    /** Used by the dispatcher to route Transfer* events when the lookup misses on payouts. */
    Optional<Refund> findByCustodyTransferId(TransferId transferId);

    List<Refund> findByInvoice(InvoiceId invoiceId);

    /**
     * Sum of non-failed refund amounts already issued for an invoice. Used by
     * {@code IssueRefundUseCase} to enforce {@code sum(refund.amount) <=
     * invoice.expectedAmount}.
     */
    BigInteger sumIssuedNonFailed(InvoiceId invoiceId);
}
