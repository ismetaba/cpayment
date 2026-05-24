package com.cpayment.custody.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TransferId(UUID value) {
    public TransferId { Objects.requireNonNull(value, "value"); }
    public static TransferId of(UUID v) { return new TransferId(v); }
}
