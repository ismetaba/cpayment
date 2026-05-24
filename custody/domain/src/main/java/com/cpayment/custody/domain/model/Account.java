package com.cpayment.custody.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record Account(
    AccountId id,
    Optional<WalletId> walletId,
    NetworkId network,
    String address,
    String label,
    Set<String> supportedAssetSymbols
) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(walletId, "walletId (use Optional.empty())");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(address, "address");
        if (address.isBlank()) throw new IllegalArgumentException("address must be non-blank");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(supportedAssetSymbols, "supportedAssetSymbols");
        supportedAssetSymbols = Set.copyOf(supportedAssetSymbols);
    }
}
