package com.cpayment.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PayoutId(UUID value) {

    public PayoutId { Objects.requireNonNull(value, "payout id required"); }

    public static PayoutId of(UUID v)    { return new PayoutId(v); }
    public static PayoutId newId()       { return new PayoutId(UUID.randomUUID()); }
}
