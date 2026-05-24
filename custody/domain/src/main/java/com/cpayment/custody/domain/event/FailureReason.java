package com.cpayment.custody.domain.event;

public enum FailureReason {
    INSUFFICIENT_BALANCE,
    INSUFFICIENT_GAS,
    NONCE_CONFLICT,
    POLICY_REJECTED,
    NETWORK_REJECTED,
    TIMEOUT,
    UNKNOWN
}
