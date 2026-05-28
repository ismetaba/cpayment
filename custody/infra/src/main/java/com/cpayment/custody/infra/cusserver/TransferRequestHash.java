package com.cpayment.custody.infra.cusserver;

import com.cpayment.core.util.Sha256;
import com.cpayment.custody.domain.model.SendTransferCommand;

/**
 * Canonical SHA-256 over the contract-relevant fields of a transfer request. Used by
 * the adapter-local idempotency store to detect "same key, different body" collisions
 * before forwarding to cus-server (which has no native idempotency support).
 */
final class TransferRequestHash {

    private TransferRequestHash() {}

    static String of(SendTransferCommand cmd) {
        return Sha256.hex(String.join("|",
            cmd.fromAddress(),
            cmd.toAddress(),
            cmd.asset().canonical(),
            cmd.amount().toString(),
            cmd.memo().orElse(""),
            cmd.feePreference().getClass().getSimpleName()
        ));
    }
}
