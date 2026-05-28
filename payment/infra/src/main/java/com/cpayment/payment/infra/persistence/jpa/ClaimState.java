package com.cpayment.payment.infra.persistence.jpa;

/** Shared lifecycle state for every two-phase idempotency-claim table. */
public enum ClaimState { PENDING, COMPLETED }
