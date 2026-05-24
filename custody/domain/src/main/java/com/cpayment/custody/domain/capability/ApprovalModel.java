package com.cpayment.custody.domain.capability;

public enum ApprovalModel {
    /** Policy auto-approves every transfer (current cus-server behavior). */
    AUTO,
    /** m-of-n approver quorum required. */
    QUORUM,
    /** Per-transfer WebAuthn-style device challenge. */
    DEVICE_CHALLENGE
}
