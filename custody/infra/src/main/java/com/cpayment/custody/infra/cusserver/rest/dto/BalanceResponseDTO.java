package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceResponseDTO(
    String networkName,
    String asset,
    String address,
    BalanceDetail detail,
    String status
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BalanceDetail(String available) {}
}
