package com.cpayment.payment.infra.persistence.jpa;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Idempotency claim for payouts. Result id is stored in {@code payout_id}. */
@Entity
@Table(name = "cpayment_payout_idempotency_claim")
@AttributeOverride(name = "resultId", column = @Column(name = "payout_id"))
public class PayoutIdempotencyClaimEntity extends AbstractIdempotencyClaimEntity {
}
