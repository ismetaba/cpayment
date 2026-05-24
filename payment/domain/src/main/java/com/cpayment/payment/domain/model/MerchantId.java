package com.cpayment.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MerchantId(UUID value) {

    public MerchantId {
        Objects.requireNonNull(value, "merchant id required");
    }

    public static MerchantId of(UUID v) { return new MerchantId(v); }
    public static MerchantId of(String v) { return new MerchantId(UUID.fromString(v)); }
}
