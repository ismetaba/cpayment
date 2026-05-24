package com.cpayment.custody.infra.cusserver.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Adapter-local mirror of cus-server's {@code CreateDepositTransactionsEvent}.
 * The analysis report shows it carries a list accessible via {@code depositTransactions()};
 * the field name below mirrors that accessor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateDepositTransactionsEventDTO(List<DepositTransactionDTO> depositTransactions) {
}
