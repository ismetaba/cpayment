package com.cpayment.custody.infra.cusserver.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Adapter-local mirror of cus-server's {@code UpdateTransactionEvent}. Analysis showed
 * it carries a list accessible via {@code getPayloads()}; we mirror the accessor name.
 *
 * <p>Field shape is conservative — unknown fields ignored — so a forward-compatible
 * cus-server release won't break the bridge.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTransactionEventDTO(List<TransactionUpdatePayloadDTO> payloads) {
}
