package com.cpayment.custody.infra.cusserver.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * Single update inside an {@link UpdateTransactionEventDTO} batch. The {@code type}
 * field is a string — cus-server's {@code TransactionUpdateType} enum — so we accept
 * unknown values gracefully and let the mapper decide.
 *
 * <p>Speculative shape — should be cross-checked against cus-server's actual
 * DTO when it stabilises.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionUpdatePayloadDTO(
    UUID transactionId,
    String type,
    String txHash,
    Integer confirmations,
    BigInteger feeActual,
    String feeNetworkName,
    String feeAssetName,
    String reason,
    UUID replacementTransactionId,
    Instant occurredAt
) {}
