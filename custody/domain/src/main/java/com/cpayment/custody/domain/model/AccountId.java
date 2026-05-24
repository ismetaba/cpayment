package com.cpayment.custody.domain.model;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {
    public AccountId { Objects.requireNonNull(value, "value"); }
    public static AccountId of(UUID v) { return new AccountId(v); }
}
