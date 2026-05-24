package com.cpayment.custody.domain.model;

import com.cpayment.core.model.IdempotencyKey;

import java.util.Optional;

public record TransferOutcome(
    IdempotencyKey requestKey,
    Optional<TransferId> transferId,
    boolean success,
    Optional<String> failureReason
) {}
