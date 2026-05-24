package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable invoice aggregate.
 *
 * <p>Lifecycle: AWAITING_DEPOSIT → DETECTED (deposit seen but below
 * {@link #minConfirmations}) → PAID (≥ {@code minConfirmations}). Underpayment short-
 * circuits to UNDERPAID regardless of confirmation depth.
 *
 * <p>Transitions go through explicit {@code markXxx} methods that return a new instance
 * — never mutate fields in place.
 */
public record Invoice(
    InvoiceId id,
    MerchantId merchantId,
    AssetId asset,
    BigInteger expectedAmount,
    int minConfirmations,
    InvoiceStatus status,
    AccountId custodyAccount,
    String depositAddress,
    Optional<String> receivedTxHash,
    Optional<BigInteger> receivedAmount,
    Optional<Integer> receivedConfirmations,
    Instant createdAt,
    Instant updatedAt
) {

    public Invoice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(expectedAmount, "expectedAmount");
        if (expectedAmount.signum() <= 0) {
            throw new IllegalArgumentException("expectedAmount must be positive");
        }
        if (minConfirmations < 1) {
            throw new IllegalArgumentException("minConfirmations must be >= 1");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(custodyAccount, "custodyAccount");
        Objects.requireNonNull(depositAddress, "depositAddress");
        if (depositAddress.isBlank()) {
            throw new IllegalArgumentException("depositAddress must be non-blank");
        }
        Objects.requireNonNull(receivedTxHash, "receivedTxHash");
        Objects.requireNonNull(receivedAmount, "receivedAmount");
        Objects.requireNonNull(receivedConfirmations, "receivedConfirmations");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Invoice newlyCreated(InvoiceId id, MerchantId merchantId, AssetId asset,
                                       BigInteger expectedAmount, int minConfirmations,
                                       AccountId custodyAccount, String depositAddress,
                                       Instant now) {
        return new Invoice(id, merchantId, asset, expectedAmount, minConfirmations,
                           InvoiceStatus.AWAITING_DEPOSIT,
                           custodyAccount, depositAddress,
                           Optional.empty(), Optional.empty(), Optional.empty(),
                           now, now);
    }

    public Invoice markDetected(String txHash, BigInteger amount, int confirmations, Instant now) {
        return copy(InvoiceStatus.DETECTED, Optional.of(txHash), Optional.of(amount),
                    Optional.of(confirmations), now);
    }

    public Invoice markPaid(String txHash, BigInteger amount, int confirmations, Instant now) {
        return copy(InvoiceStatus.PAID, Optional.of(txHash), Optional.of(amount),
                    Optional.of(confirmations), now);
    }

    public Invoice markUnderpaid(String txHash, BigInteger amount, int confirmations, Instant now) {
        return copy(InvoiceStatus.UNDERPAID, Optional.of(txHash), Optional.of(amount),
                    Optional.of(confirmations), now);
    }

    public Invoice markExpired(Instant now) {
        return copy(InvoiceStatus.EXPIRED, receivedTxHash, receivedAmount, receivedConfirmations, now);
    }

    public Invoice markCancelled(Instant now) {
        return copy(InvoiceStatus.CANCELLED, receivedTxHash, receivedAmount, receivedConfirmations, now);
    }

    private Invoice copy(InvoiceStatus newStatus, Optional<String> txHash,
                         Optional<BigInteger> amount, Optional<Integer> confirmations, Instant now) {
        return new Invoice(id, merchantId, asset, expectedAmount, minConfirmations, newStatus,
                           custodyAccount, depositAddress, txHash, amount, confirmations,
                           createdAt, now);
    }
}
