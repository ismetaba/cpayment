package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeeResponseDTO(
    BigInteger estimatedFee,
    String feeAsset
) {}
