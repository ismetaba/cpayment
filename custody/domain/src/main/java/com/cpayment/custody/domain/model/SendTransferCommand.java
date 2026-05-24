package com.cpayment.custody.domain.model;

import com.cpayment.core.model.IdempotencyKey;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * idempotencyKey is REQUIRED — adapters dedupe via local table.
 * fromAddress is required because cus-server addresses transfers by chain address, not
 * by AccountId; the caller already knows it (stored on the invoice / wallet).
 * memo is honored only when capabilities advertise MEMO_SUPPORTED; otherwise the
 * adapter logs a warning and proceeds without it.
 */
public record SendTransferCommand(
    IdempotencyKey idempotencyKey,
    AccountId fromAccount,
    String fromAddress,
    String toAddress,
    AssetId asset,
    BigInteger amount,
    Optional<String> memo,
    FeePreference feePreference
) {

    public SendTransferCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(fromAccount, "fromAccount");
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
