package com.cpayment.custody.domain.model;

import com.cpayment.core.model.IdempotencyKey;

import java.math.BigInteger;
import java.util.Optional;

/**
 * idempotencyKey is REQUIRED — adapters dedupe via local table.
 * memo is honored only when capabilities advertise MEMO_SUPPORTED;
 * otherwise the adapter logs a warning and proceeds without it.
 */
public record SendTransferCommand(
    IdempotencyKey idempotencyKey,
    AccountId fromAccount,
    String toAddress,
    AssetId asset,
    BigInteger amount,
    Optional<String> memo,
    FeePreference feePreference
) {

    public SendTransferCommand {
        if (idempotencyKey == null) throw new IllegalArgumentException("idempotencyKey required");
        if (fromAccount == null) throw new IllegalArgumentException("fromAccount required");
        if (toAddress == null || toAddress.isBlank()) throw new IllegalArgumentException("toAddress required");
        if (asset == null) throw new IllegalArgumentException("asset required");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount > 0");
        if (memo == null) memo = Optional.empty();
        if (feePreference == null) feePreference = FeePreference.NORMAL;
    }
}
