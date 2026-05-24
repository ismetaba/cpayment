package com.cpayment.custody.domain.model;

import java.math.BigInteger;

/**
 * Sealed fee preference — closed set of cpayment-level options.
 * Provider-specific fee strategies (cus-server's LEVELED/RATED/etc.) are mapped
 * by adapter's FeeStrategyMapper, not exposed here.
 */
public sealed interface FeePreference {

    Economy ECONOMY = new Economy();
    Normal NORMAL = new Normal();
    Fast FAST = new Fast();

    record Economy() implements FeePreference {}
    record Normal() implements FeePreference {}
    record Fast() implements FeePreference {}

    /** Caller-provided raw values; adapter chooses how to interpret per network. */
    record Custom(BigInteger maxFeePerUnit, BigInteger priorityTip) implements FeePreference {}
}
