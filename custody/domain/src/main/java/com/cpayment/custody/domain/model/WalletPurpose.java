package com.cpayment.custody.domain.model;

public enum WalletPurpose {
    /** Holds merchant funds — deposit addresses and payout source. */
    MERCHANT,
    /** Funds native-coin gas for outbound transfers from MERCHANT wallets. */
    GAS_FUNDER,
    /** Long-term treasury (cold/warm) reserve. */
    TREASURY
}
