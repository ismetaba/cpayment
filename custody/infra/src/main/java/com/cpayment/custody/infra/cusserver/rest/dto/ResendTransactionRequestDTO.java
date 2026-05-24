package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigInteger;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendTransactionRequestDTO(
    UUID replacedTxId,
    String fromAddress,
    String toAddress,
    BigInteger amount,
    String networkName,
    String assetName,
    String feeStrategy
) {}
