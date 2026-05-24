package com.cpayment.custody.infra.cusserver.rest.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Mirrors cus-server's {@code AddAccountRequestDTO}. Nested {@link Account} matches
 * its inner {@code AccountDTO}.
 */
public record AddAccountRequest(UUID walletId, Account account) {

    public record Account(String label, String networkName, Set<String> supportedAssets) {}
}
