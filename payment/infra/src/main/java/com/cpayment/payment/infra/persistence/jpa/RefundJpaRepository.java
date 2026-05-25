package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundJpaRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByCustodyTransferId(UUID custodyTransferId);

    List<RefundEntity> findByInvoiceId(UUID invoiceId);

    /**
     * Sum of amounts for refunds against an invoice that are NOT in {@code FAILED}.
     *
     * <p>Returns {@link BigDecimal} (not {@code BigInteger}) because Hibernate widens
     * {@code sum(NUMERIC(38,0))} to {@code BigDecimal} on Oracle / PostgreSQL — a
     * {@code BigInteger} return type would risk {@link ClassCastException} at runtime.
     * The repository adapter converts via {@code toBigInteger()}.
     */
    @Query("""
        select coalesce(sum(r.amount), 0)
          from RefundEntity r
         where r.invoiceId = :invoiceId
           and r.status <> :failedStatus
        """)
    BigDecimal sumNonFailedAmounts(@Param("invoiceId") UUID invoiceId,
                                   @Param("failedStatus") RefundStatus failedStatus);
}
