package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable payout aggregate. {@link #custodyTransferId} is populated when cus-server
 * accepts the request; downstream Transfer* events advance the status via the explicit
 * {@code markXxx} transitions.
 */
public record Payout(
    PayoutId id,
    MerchantId merchantId,
    AssetId asset,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    Optional<String> memo,
    PayoutStatus status,
    Optional<TransferId> custodyTransferId,
    Optional<String> txHash,
    Optional<Integer> confirmations,
    Optional<BigInteger> feeActual,
    Optional<AssetId> feeAsset,
    Optional<FailureReason> failureReason,
    Optional<String> failureMessage,
    Optional<TransferId> replacedBy,
    Instant createdAt,
    Instant updatedAt
) {

    public Payout {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(fromAddress, "fromAddress");
        if (fromAddress.isBlank()) throw new IllegalArgumentException("fromAddress must be non-blank");
        Objects.requireNonNull(toAddress, "toAddress");
        if (toAddress.isBlank()) throw new IllegalArgumentException("toAddress must be non-blank");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        Objects.requireNonNull(memo, "memo");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(custodyTransferId, "custodyTransferId");
        Objects.requireNonNull(txHash, "txHash");
        Objects.requireNonNull(confirmations, "confirmations");
        Objects.requireNonNull(feeActual, "feeActual");
        Objects.requireNonNull(feeAsset, "feeAsset");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(failureMessage, "failureMessage");
        Objects.requireNonNull(replacedBy, "replacedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Payout requested(PayoutId id, MerchantId merchant, AssetId asset,
                                   String fromAddress, String toAddress, BigInteger amount,
                                   Optional<String> memo, Instant now) {
        return new Payout(id, merchant, asset, fromAddress, toAddress, amount, memo,
            PayoutStatus.REQUESTED,
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(),
            now, now);
    }

    public Payout markSubmitted(TransferId transferId, Instant now) {
        return copy(PayoutStatus.SUBMITTED, Optional.of(transferId), txHash, confirmations,
            feeActual, feeAsset, failureReason, failureMessage, replacedBy, now);
    }

    public Payout markBroadcast(String txHash, Instant now) {
        return copy(PayoutStatus.BROADCAST, custodyTransferId, Optional.of(txHash), confirmations,
            feeActual, feeAsset, failureReason, failureMessage, replacedBy, now);
    }

    public Payout markConfirmed(String txHash, int confirmations,
                                BigInteger feeActual, AssetId feeAsset, Instant now) {
        return copy(PayoutStatus.CONFIRMED, custodyTransferId, Optional.of(txHash),
            Optional.of(confirmations), Optional.of(feeActual), Optional.of(feeAsset),
            failureReason, failureMessage, replacedBy, now);
    }

    public Payout markFailed(FailureReason reason, String message, Instant now) {
        return copy(PayoutStatus.FAILED, custodyTransferId, txHash, confirmations,
            feeActual, feeAsset, Optional.of(reason), Optional.of(message), replacedBy, now);
    }

    public Payout markReplaced(TransferId newTransferId, Instant now) {
        return copy(PayoutStatus.REPLACED, custodyTransferId, txHash, confirmations,
            feeActual, feeAsset, failureReason, failureMessage, Optional.of(newTransferId), now);
    }

    public Payout markCancelled(Instant now) {
        return copy(PayoutStatus.CANCELLED, custodyTransferId, txHash, confirmations,
            feeActual, feeAsset, failureReason, failureMessage, replacedBy, now);
    }

    private Payout copy(PayoutStatus newStatus, Optional<TransferId> tx, Optional<String> hash,
                        Optional<Integer> conf, Optional<BigInteger> fee, Optional<AssetId> feeAst,
                        Optional<FailureReason> reason, Optional<String> msg,
                        Optional<TransferId> replacedBy, Instant now) {
        return new Payout(id, merchantId, asset, fromAddress, toAddress, amount, memo,
            newStatus, tx, hash, conf, fee, feeAst, reason, msg, replacedBy, createdAt, now);
    }
}
