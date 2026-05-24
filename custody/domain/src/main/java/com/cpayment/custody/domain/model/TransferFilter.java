package com.cpayment.custody.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public record TransferFilter(
    Optional<AccountId> fromAccount,
    Optional<NetworkId> network,
    Optional<String> assetSymbol,
    Set<TransferState> states,
    Optional<Instant> createdFrom,
    Optional<Instant> createdTo,
    PageRequest page
) {}
