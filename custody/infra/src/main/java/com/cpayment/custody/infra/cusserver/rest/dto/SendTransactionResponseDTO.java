package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SendTransactionResponseDTO(UUID id, String message) {
}
