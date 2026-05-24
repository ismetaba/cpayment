package com.cpayment.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public record InvoiceId(UUID value) {

    public InvoiceId {
        Objects.requireNonNull(value, "invoice id required");
    }

    public static InvoiceId of(UUID v) { return new InvoiceId(v); }
    public static InvoiceId newId() { return new InvoiceId(UUID.randomUUID()); }
}
