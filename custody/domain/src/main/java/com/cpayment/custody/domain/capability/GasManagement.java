package com.cpayment.custody.domain.capability;

public enum GasManagement {
    /** cpayment funds gas via a dedicated GAS_FUNDER wallet (current cus-server model). */
    CPAYMENT_OWNED,
    /** Provider auto-funds gas internally; cpayment only tops up the master wallet. */
    CUSTODY_INTERNAL
}
