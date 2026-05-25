package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.payment.domain.model.RefundReason;
import com.cpayment.payment.domain.model.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cpayment_refund")
public class RefundEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "asset_canonical", nullable = false, length = 80)
    private String assetCanonical;

    @Column(name = "from_address", nullable = false, length = 120)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 120)
    private String toAddress;

    @Column(name = "amount", nullable = false, precision = 38)
    private BigInteger amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private RefundReason reason;

    @Column(name = "memo", length = 200)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "custody_transfer_id", nullable = false, unique = true)
    private UUID custodyTransferId;

    @Column(name = "tx_hash", length = 120)
    private String txHash;

    @Column(name = "confirmations")
    private Integer confirmations;

    @Column(name = "fee_actual", precision = 38)
    private BigInteger feeActual;

    @Column(name = "fee_asset_canonical", length = 80)
    private String feeAssetCanonical;

    @Column(name = "failure_reason", length = 40)
    private String failureReason;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public String getAssetCanonical() { return assetCanonical; }
    public void setAssetCanonical(String assetCanonical) { this.assetCanonical = assetCanonical; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
    public BigInteger getAmount() { return amount; }
    public void setAmount(BigInteger amount) { this.amount = amount; }
    public RefundReason getReason() { return reason; }
    public void setReason(RefundReason reason) { this.reason = reason; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    public UUID getCustodyTransferId() { return custodyTransferId; }
    public void setCustodyTransferId(UUID custodyTransferId) { this.custodyTransferId = custodyTransferId; }
    public String getTxHash() { return txHash; }
    public void setTxHash(String txHash) { this.txHash = txHash; }
    public Integer getConfirmations() { return confirmations; }
    public void setConfirmations(Integer confirmations) { this.confirmations = confirmations; }
    public BigInteger getFeeActual() { return feeActual; }
    public void setFeeActual(BigInteger feeActual) { this.feeActual = feeActual; }
    public String getFeeAssetCanonical() { return feeAssetCanonical; }
    public void setFeeAssetCanonical(String feeAssetCanonical) { this.feeAssetCanonical = feeAssetCanonical; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
