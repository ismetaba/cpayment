package com.cpayment.payment.infra.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

import com.cpayment.payment.domain.model.InvoiceStatus;

/**
 * Storage-shape mirror of {@link com.cpayment.payment.domain.model.Invoice}. Kept
 * intentionally as a mutable JavaBean so JPA can hydrate it; the domain record is
 * still the source of truth — this class only exists at the persistence boundary.
 */
@Entity
@Table(name = "cpayment_invoice")
public class InvoiceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "asset_canonical", nullable = false, length = 80)
    private String assetCanonical;

    @Column(name = "expected_amount", nullable = false, precision = 38)
    private BigInteger expectedAmount;

    @Column(name = "min_confirmations", nullable = false)
    private int minConfirmations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "custody_account_id", nullable = false, unique = true)
    private UUID custodyAccountId;

    @Column(name = "deposit_address", nullable = false, length = 120)
    private String depositAddress;

    @Column(name = "received_tx_hash", length = 120)
    private String receivedTxHash;

    @Column(name = "received_amount", precision = 38)
    private BigInteger receivedAmount;

    @Column(name = "received_confirmations")
    private Integer receivedConfirmations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public String getAssetCanonical() { return assetCanonical; }
    public void setAssetCanonical(String assetCanonical) { this.assetCanonical = assetCanonical; }
    public BigInteger getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigInteger expectedAmount) { this.expectedAmount = expectedAmount; }
    public int getMinConfirmations() { return minConfirmations; }
    public void setMinConfirmations(int minConfirmations) { this.minConfirmations = minConfirmations; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public UUID getCustodyAccountId() { return custodyAccountId; }
    public void setCustodyAccountId(UUID custodyAccountId) { this.custodyAccountId = custodyAccountId; }
    public String getDepositAddress() { return depositAddress; }
    public void setDepositAddress(String depositAddress) { this.depositAddress = depositAddress; }
    public String getReceivedTxHash() { return receivedTxHash; }
    public void setReceivedTxHash(String receivedTxHash) { this.receivedTxHash = receivedTxHash; }
    public BigInteger getReceivedAmount() { return receivedAmount; }
    public void setReceivedAmount(BigInteger receivedAmount) { this.receivedAmount = receivedAmount; }
    public Integer getReceivedConfirmations() { return receivedConfirmations; }
    public void setReceivedConfirmations(Integer receivedConfirmations) { this.receivedConfirmations = receivedConfirmations; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
