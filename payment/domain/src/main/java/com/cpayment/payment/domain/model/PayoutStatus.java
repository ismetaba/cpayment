package com.cpayment.payment.domain.model;

public enum PayoutStatus {

    /** Command accepted, claim taken; not yet sent to custody. */
    REQUESTED,

    /** cus-server returned a TransferId; sitting in its queue / awaiting signature. */
    SUBMITTED,

    /** Custody published TransferBroadcast — tx is on-chain mempool. */
    BROADCAST,

    /** Custody published TransferConfirmed — required depth reached. */
    CONFIRMED,

    /** Custody published TransferFailed — terminal. */
    FAILED,

    /** Custody published TransferReplaced — superseded by a new payout (RBF / speed-up). */
    REPLACED,

    /** Operator-cancelled before broadcast. */
    CANCELLED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == FAILED || this == CANCELLED || this == REPLACED;
    }
}
