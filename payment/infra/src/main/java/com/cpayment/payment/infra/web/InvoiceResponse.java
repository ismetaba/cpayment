package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceStatus;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID merchantId,
    String asset,
    BigInteger expectedAmount,
    InvoiceStatus status,
    String depositAddress,
    Instant createdAt
) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
            invoice.id().value(),
            invoice.merchantId().value(),
            invoice.asset().canonical(),
            invoice.expectedAmount(),
            invoice.status(),
            invoice.depositAddress(),
            invoice.createdAt()
        );
    }
}
