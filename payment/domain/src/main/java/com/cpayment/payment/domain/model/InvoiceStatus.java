package com.cpayment.payment.domain.model;

public enum InvoiceStatus {

    /** Address provisioned; awaiting on-chain deposit. */
    AWAITING_DEPOSIT,

    /** Deposit detected on-chain; mempool seen or first confirmation observed. */
    DETECTED,

    /** Deposit confirmed to required depth. */
    PAID,

    /** Deposit observed but below the expected amount. Terminal — the merchant must
     * issue a new invoice; subsequent deposit events for this invoice are ignored. */
    UNDERPAID,

    /** Expired without a deposit. */
    EXPIRED,

    /** Cancelled by the merchant. */
    CANCELLED;

    public boolean isTerminal() {
        return this == PAID || this == UNDERPAID || this == EXPIRED || this == CANCELLED;
    }
}
