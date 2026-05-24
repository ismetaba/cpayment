package com.cpayment.custody.infra.cusserver.rest.dto;

import java.util.Set;

public record BalanceRequestDTO(String network, Set<String> asset, String address) {
}
