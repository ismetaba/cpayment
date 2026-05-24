package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AssetId;

/**
 * Conservative default minimum confirmation depths per network. Kept as a simple
 * lookup so callers (e.g. controllers defaulting an absent request field) don't have
 * to repeat the table. Merchants can override per invoice through
 * {@link CreateInvoiceCommand}.
 *
 * <p>Values reflect industry conventions for "good enough that a reorg is unlikely":
 * <ul>
 *   <li>ETH mainnet: 12 (~3 minutes)</li>
 *   <li>BTC mainnet: 3 (~30 minutes; 6 for very high-value payments)</li>
 *   <li>TRON mainnet: 19 (~1 minute, equivalent to one full SR round)</li>
 *   <li>Solana mainnet: 32 (full slot finalization)</li>
 *   <li>XRP mainnet: 1 (ledger close = effectively final)</li>
 *   <li>Unknown: 6, a moderate default that's safe for most chains.</li>
 * </ul>
 */
public final class NetworkConfirmations {

    private NetworkConfirmations() {}

    public static int defaultFor(AssetId asset) {
        return switch (asset.network().chain()) {
            case "eth"  -> 12;
            case "btc"  -> 3;
            case "tron" -> 19;
            case "sol"  -> 32;
            case "xrp"  -> 1;
            default     -> 6;
        };
    }
}
