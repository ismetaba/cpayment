package com.cpayment.custody.domain.capability;

public enum OptionalFeature {
    /** Adapter dedupes transfer requests via a local idempotency table. */
    LOCAL_IDEMPOTENCY,
    /** Memo / destination-tag (XRP, XLM, Cosmos…) supported on send. */
    MEMO,
    /** Replace-by-fee / speedUp on pending transfers. */
    RBF,
    /** Address validation performed in adapter before calling provider. */
    ADDRESS_VALIDATION_LOCAL,
    /** Adapter offers counterparty screening (KYT). */
    SCREENING,
    /** Cancel a pending transfer before broadcast. */
    CANCEL
}
