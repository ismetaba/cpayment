package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeeRequestDTO(
    String fromAddress,
    String toAddress,
    BigInteger amount
) {}
