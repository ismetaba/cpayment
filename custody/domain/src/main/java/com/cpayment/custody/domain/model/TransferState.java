package com.cpayment.custody.domain.model;

/**
 * Outbound transfer lifecycle, normalized across providers.
 * cus-server flow: SUBMITTED -> AWAITING_SIGNATURE -> SIGNED -> BROADCAST -> CONFIRMED|FAILED.
 * A 200 from POST /holder/transactions means SUBMITTED, NOT broadcast.
 */
public enum TransferState {
    SUBMITTED,
    AWAITING_SIGNATURE,
    AWAITING_APPROVAL,
    SIGNED,
    BROADCAST,
    CONFIRMED,
    REPLACED,
    FAILED,
    CANCELLED
}
