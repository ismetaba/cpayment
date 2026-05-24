package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;
import java.util.UUID;

/**
 * Mirrors cus-server's {@code CreateHolderAccountResponse}. The address field name is
 * speculative based on the analysis report; we accept additional fields without failing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateHolderAccountResponse(
    UUID id,
    String label,
    String networkName,
    String address,
    Set<String> supportedAssets
) {}
