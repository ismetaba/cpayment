package com.cpayment.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RefundId(UUID value) {

    public RefundId { Objects.requireNonNull(value, "refund id required"); }

    public static RefundId of(UUID v)  { return new RefundId(v); }
    public static RefundId newId()     { return new RefundId(UUID.randomUUID()); }
}
