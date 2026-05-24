package com.cpayment.custody.domain.model;

import java.util.Set;

public record CreateAccountCommand(
    WalletId walletId,
    NetworkId network,
    String label,
    Set<String> supportedAssetSymbols
) {}
