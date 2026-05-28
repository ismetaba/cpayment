package com.cpayment.payment.infra.persistence.jpa;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Persistent backing for the custody-side {@code TransferIdempotencyStore}. Lives in
 * payment-infra because that module already owns the JPA / Liquibase / DataSource setup;
 * custody-infra would otherwise need to duplicate it. Result id (the cus-server
 * {@code TransferId}) is stored in {@code transfer_id}.
 */
@Entity
@Table(name = "cpayment_custody_idempotency_claim")
@AttributeOverride(name = "resultId", column = @Column(name = "transfer_id"))
public class CustodyIdempotencyClaimEntity extends AbstractIdempotencyClaimEntity {
}
