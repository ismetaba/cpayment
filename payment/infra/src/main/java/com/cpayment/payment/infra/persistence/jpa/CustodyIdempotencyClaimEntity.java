package com.cpayment.payment.infra.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent backing for the custody-side {@code TransferIdempotencyStore}. Lives in
 * payment-infra because that module already owns the JPA / Liquibase / DataSource
 * setup; custody-infra would otherwise need to duplicate it.
 *
 * <p>Schema mirrors the other idempotency-claim tables (invoice, payout, refund),
 * but stores a {@code transfer_id} instead of a resource id.
 */
@Entity
@Table(name = "cpayment_custody_idempotency_claim")
public class CustodyIdempotencyClaimEntity {

    public enum State { PENDING, COMPLETED }

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private State state;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
