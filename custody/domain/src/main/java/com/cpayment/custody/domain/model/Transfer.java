package com.cpayment.custody.domain.model;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

public record Transfer(
    TransferId id,
    TransferState state,
    AccountId fromAccount,
    String toAddress,
    AssetId asset,
    BigInteger amount,
    Optional<String> txHash,
    Optional<BigInteger> feeActual,
    Instant createdAt,
    Instant updatedAt
) {}
