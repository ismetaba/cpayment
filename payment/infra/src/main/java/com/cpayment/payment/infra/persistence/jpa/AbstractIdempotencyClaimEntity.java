package com.cpayment.payment.infra.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared mapping for the two-phase idempotency-claim tables (invoice, payout, refund,
 * custody-transfer). The four tables are structurally identical — {@code key} primary
 * key, request hash, lifecycle state, timestamps, and a single result id — differing
 * only in their table name and the <em>column name</em> of the result id. Concrete
 * subclasses supply both via {@code @Table} and {@code @AttributeOverride("resultId")},
 * so the row shape and the dedupe logic ({@link AbstractJpaIdempotencyStore}) live in
 * exactly one place.
 */
@MappedSuperclass
public abstract class AbstractIdempotencyClaimEntity {

    @Id
    @Column(name = "key", nullable = false, updatable = false, length = 128)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private ClaimState state;

    /** Id of the completed resource (invoice/payout/refund/transfer). Column name is
     * remapped per table via {@code @AttributeOverride} on the concrete entity. */
    @Column(name = "result_id")
    private UUID resultId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public ClaimState getState() { return state; }
    public void setState(ClaimState state) { this.state = state; }
    public UUID getResultId() { return resultId; }
    public void setResultId(UUID resultId) { this.resultId = resultId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
