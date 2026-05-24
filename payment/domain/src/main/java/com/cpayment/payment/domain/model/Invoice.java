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
 * <p>Lifecycle transitions go through explicit {@code markXxx} methods that return
 * a new instance with the updated state — never mutate fields in place.
 *
 * <p>{@link #custodyAccount} is the bridge to the custody bounded context. cpayment
 * keeps the mapping so that when a deposit event arrives carrying an AccountId,
 * we can resolve the corresponding invoice without leaking custody concerns elsewhere.
 */
public record Invoice(
    InvoiceId id,
    MerchantId merchantId,
    AssetId asset,
    BigInteger expectedAmount,
    InvoiceStatus status,
    AccountId custodyAccount,
    String depositAddress,
    Optional<String> receivedTxHash,
    Optional<BigInteger> receivedAmount,
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
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(custodyAccount, "custodyAccount");
        Objects.requireNonNull(depositAddress, "depositAddress");
        if (depositAddress.isBlank()) {
            throw new IllegalArgumentException("depositAddress must be non-blank");
        }
        Objects.requireNonNull(receivedTxHash, "receivedTxHash");
        Objects.requireNonNull(receivedAmount, "receivedAmount");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Invoice newlyCreated(InvoiceId id, MerchantId merchantId, AssetId asset,
                                       BigInteger expectedAmount, AccountId custodyAccount,
                                       String depositAddress, Instant now) {
        return new Invoice(id, merchantId, asset, expectedAmount,
                           InvoiceStatus.AWAITING_DEPOSIT,
                           custodyAccount, depositAddress,
                           Optional.empty(), Optional.empty(),
                           now, now);
    }

    public Invoice markDetected(String txHash, BigInteger amount, Instant now) {
        return copy(InvoiceStatus.DETECTED, Optional.of(txHash), Optional.of(amount), now);
    }

    public Invoice markPaid(String txHash, BigInteger amount, Instant now) {
        return copy(InvoiceStatus.PAID, Optional.of(txHash), Optional.of(amount), now);
    }

    public Invoice markUnderpaid(String txHash, BigInteger amount, Instant now) {
        return copy(InvoiceStatus.UNDERPAID, Optional.of(txHash), Optional.of(amount), now);
    }

    public Invoice markExpired(Instant now) {
        return copy(InvoiceStatus.EXPIRED, receivedTxHash, receivedAmount, now);
    }

    public Invoice markCancelled(Instant now) {
        return copy(InvoiceStatus.CANCELLED, receivedTxHash, receivedAmount, now);
    }

    private Invoice copy(InvoiceStatus newStatus, Optional<String> txHash,
                         Optional<BigInteger> amount, Instant now) {
        return new Invoice(id, merchantId, asset, expectedAmount, newStatus,
                           custodyAccount, depositAddress, txHash, amount, createdAt, now);
    }
}
