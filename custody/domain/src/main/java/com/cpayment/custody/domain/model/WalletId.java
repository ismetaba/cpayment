package com.cpayment.custody.domain.model;

import java.util.Objects;
import java.util.UUID;

public record WalletId(UUID value) {
    public WalletId { Objects.requireNonNull(value, "value"); }
    public static WalletId of(UUID v) { return new WalletId(v); }
}
