package com.cpayment.payment.domain.usecase;

import com.cpayment.core.util.Sha256;
import com.cpayment.payment.domain.model.CreateInvoiceCommand;

/**
 * Deterministic hash of an invoice-creation request — used by the idempotency store to
 * detect "same key, different body" conflicts.
 *
 * <p>The canonical string concatenates fields in a fixed order with a delimiter that
 * cannot appear inside any of them (UUIDs and BigInteger toString are safe). SHA-256 is
 * overkill for collision avoidance here but trivial to compute and well understood —
 * preferred over hashCode() which collides too easily and varies by JVM.
 */
public final class InvoiceRequestHash {

    private InvoiceRequestHash() {}

    public static String of(CreateInvoiceCommand command) {
        return Sha256.hex(String.join("|",
            command.merchantId().value().toString(),
            command.asset().canonical(),
            command.expectedAmount().toString()
        ));
    }
}
