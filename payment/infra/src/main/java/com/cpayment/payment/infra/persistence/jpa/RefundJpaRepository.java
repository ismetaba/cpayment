package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundJpaRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByCustodyTransferId(UUID custodyTransferId);

    List<RefundEntity> findByInvoiceId(UUID invoiceId);

    /** Sum of amounts for refunds against an invoice that are NOT in {@code FAILED}. */
    @Query("""
        select coalesce(sum(r.amount), 0)
          from RefundEntity r
         where r.invoiceId = :invoiceId
           and r.status <> :failedStatus
        """)
    BigInteger sumNonFailedAmounts(@Param("invoiceId") UUID invoiceId,
                                   @Param("failedStatus") RefundStatus failedStatus);
}
