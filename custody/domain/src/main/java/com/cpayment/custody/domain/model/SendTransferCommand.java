package com.cpayment.custody.domain.model;

import com.cpayment.core.model.IdempotencyKey;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Custody-agnostic transfer request.
 *
 * <ul>
 *   <li>{@code idempotencyKey} REQUIRED — adapter dedupes via two-phase claim.</li>
 *   <li>{@code fromAddress} is the on-chain source; cus-server addresses transfers by
 *       chain address, not by AccountId. (An earlier draft also carried an AccountId;
 *       removed because no adapter actually used it and payout callers had to fabricate
 *       a placeholder, leaking the abstraction.)</li>
 *   <li>{@code memo} is honored only when capabilities advertise MEMO_SUPPORTED;
 *       otherwise the adapter logs a warning and proceeds without it.</li>
 * </ul>
 */
public record SendTransferCommand(
    IdempotencyKey idempotencyKey,
    String fromAddress,
    String toAddress,
    AssetId asset,
    BigInteger amount,
    Optional<String> memo,
    FeePreference feePreference
) {

    public SendTransferCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("fromAddress required");
        }
        if (toAddress == null || toAddress.isBlank()) {
            throw new IllegalArgumentException("toAddress required");
        }
        Objects.requireNonNull(asset, "asset");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount > 0");
        }
        if (memo == null) memo = Optional.empty();
        if (feePreference == null) feePreference = FeePreference.NORMAL;
    }
}
