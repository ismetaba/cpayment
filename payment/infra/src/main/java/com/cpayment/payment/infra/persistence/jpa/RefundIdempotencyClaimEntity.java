package com.cpayment.payment.infra.persistence.jpa;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Idempotency claim for refunds. Result id is stored in {@code refund_id}. */
@Entity
@Table(name = "cpayment_refund_idempotency_claim")
@AttributeOverride(name = "resultId", column = @Column(name = "refund_id"))
public class RefundIdempotencyClaimEntity extends AbstractIdempotencyClaimEntity {
}
