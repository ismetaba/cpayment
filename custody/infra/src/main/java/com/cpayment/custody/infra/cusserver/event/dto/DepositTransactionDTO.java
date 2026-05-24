package com.cpayment.custody.infra.cusserver.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * Adapter-local mirror of cus-server's per-deposit payload. Field names are speculative
 * based on the analysis report and naming conventions visible in the controllers; once
 * we depend on cus-server's common-domain JAR (or co-publish a stable event schema),
 * this DTO should be replaced with the canonical class.
 *
 * <p>Unknown fields are accepted silently so a forward-compatible cus-server release
 * does not break the bridge.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DepositTransactionDTO(
    UUID id,
    UUID accountId,
    String fromAddress,
    String networkName,
    String assetName,
    BigInteger amount,
    String txHash,
    Integer confirmations,
    Instant detectedAt
) {}
