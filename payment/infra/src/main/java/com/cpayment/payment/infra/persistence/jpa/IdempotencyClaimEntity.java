package com.cpayment.payment.infra.persistence.jpa;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Idempotency claim for invoice creation. Result id is stored in {@code invoice_id}. */
@Entity
@Table(name = "cpayment_idempotency_claim")
@AttributeOverride(name = "resultId", column = @Column(name = "invoice_id"))
public class IdempotencyClaimEntity extends AbstractIdempotencyClaimEntity {
}
