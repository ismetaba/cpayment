package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

/**
 * Sealed refund aggregate. Mirrors {@link Payout}'s typestate model but with a
 * shorter lifecycle (no cancel — refund issuance is the explicit cancellation of a
 * prior receipt — and no replace).
 *
 * <h2>Variants</h2>
 * <ul>
 *   <li>{@link IssuedRefund}    — cus-server accepted; not on-chain yet.</li>
 *   <li>{@link BroadcastRefund} — has tx hash.</li>
 *   <li>{@link ConfirmedRefund} — terminal success.</li>
 *   <li>{@link FailedRefund}    — terminal failure; tx hash optional.</li>
 * </ul>
 *
 * <p>Every refund references the original {@link InvoiceId} so the audit story is
 * trivial: "list all refunds for invoice X". The persistent invariant
 * {@code sum(refunds.amount) <= invoice.expectedAmount} is enforced at issuance time
 * by the use case.
 */
public sealed interface Refund
    permits IssuedRefund, BroadcastRefund, ConfirmedRefund, FailedRefund {

    RefundId id();
    InvoiceId invoiceId();
    MerchantId merchantId();
    AssetId asset();
    String fromAddress();
    String toAddress();
    BigInteger amount();
    RefundReason reason();
    Optional<String> memo();
    TransferId custodyTransferId();
    Instant createdAt();
    Instant updatedAt();

    RefundStatus status();
}
