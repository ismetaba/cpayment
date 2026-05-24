package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountResponse(
    UUID id,
    String label,
    String networkName,
    String address,
    Set<String> supportedAssets
) {}
