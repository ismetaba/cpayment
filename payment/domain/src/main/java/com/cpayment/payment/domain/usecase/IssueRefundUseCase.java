package com.cpayment.payment.domain.usecase;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.FeePreference;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferPort;
import com.cpayment.payment.domain.exception.InvoiceNotFoundException;
import com.cpayment.payment.domain.exception.InvoiceNotRefundableException;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceStatus;
import com.cpayment.payment.domain.model.IssueRefundCommand;
import com.cpayment.payment.domain.model.IssuedRefund;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundCreatedResult;
import com.cpayment.payment.domain.model.RefundEvent;
import com.cpayment.payment.domain.model.RefundEventType;
import com.cpayment.payment.domain.model.RefundId;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.RefundIdempotencyStore;
import com.cpayment.payment.domain.port.RefundMutationGateway;
import com.cpayment.payment.domain.port.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Issues a refund against a previously-PAID invoice.
 *
 * <h2>Validation</h2>
 * <ul>
 *   <li>Invoice must exist and be in {@link InvoiceStatus#PAID}.</li>
 *   <li>{@code sum(refunds.amount) + newRefund.amount <= invoice.expectedAmount} — partial
 *       refunds are allowed, but the merchant cannot refund more than was received.</li>
 *   <li>Refund asset must match the invoice asset — cross-asset refunds are out of scope.</li>
 * </ul>
 *
 * <h2>Idempotency + crash-window</h2>
 * <p>Same two-phase semantics as {@code CreateInvoiceUseCase} / {@code ExecutePayoutUseCase}.
 * Failure before custody → release claim; failure after custody → leave PENDING (manual
 * reconciliation via the returned TransferId).
 */
public final class IssueRefundUseCase {

    private static final Logger log = LoggerFactory.getLogger(IssueRefundUseCase.class);

    private final InvoiceRepository invoices;
    private final RefundRepository refunds;
    private final RefundIdempotencyStore idempotency;
    private final RefundMutationGateway gateway;
    private final TransferPort transfers;
    private final Clock clock;

    public IssueRefundUseCase(InvoiceRepository invoices,
                              RefundRepository refunds,
                              RefundIdempotencyStore idempotency,
                              RefundMutationGateway gateway,
                              TransferPort transfers,
                              Clock clock) {
        this.invoices = Objects.requireNonNull(invoices, "invoices");
        this.refunds = Objects.requireNonNull(refunds, "refunds");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RefundCreatedResult execute(IssueRefundCommand command) {
        Objects.requireNonNull(command, "command");

        String hash = RefundRequestHash.of(command);
        Optional<Refund> cached = idempotency.beginClaim(command.idempotencyKey(), hash);
        if (cached.isPresent()) {
            log.info("refund.idempotent-hit key={} refund={}",
                command.idempotencyKey().value(), cached.get().id().value());
            return new RefundCreatedResult(cached.get());
        }

        Invoice invoice = invoices.findById(command.invoiceId())
            .orElseThrow(() -> {
                idempotency.releaseClaim(command.idempotencyKey(), hash);
                return new InvoiceNotFoundException(command.invoiceId());
            });
        validateInvoiceRefundable(invoice, command);

        TransferId transferId;
        try {
            transferId = sendToCustody(invoice, command);
        } catch (RuntimeException preCustody) {
            idempotency.releaseClaim(command.idempotencyKey(), hash);
            log.warn("refund.create-failed-before-custody key={} reason={}",
                command.idempotencyKey().value(), preCustody.getMessage());
            throw preCustody;
        }

        try {
            Instant now = clock.instant();
            IssuedRefund issued = IssuedRefund.fresh(
                RefundId.newId(),
                invoice.id(),
                invoice.merchantId(),
                invoice.asset(),
                command.fromAddress(),
                command.toAddress(),
                command.amount(),
                command.reason(),
                command.memo(),
                transferId,
                now
            );

            gateway.apply(issued,
                List.of(RefundEvent.of(RefundEventType.REFUND_ISSUED, issued)));
            idempotency.completeClaim(command.idempotencyKey(), hash, issued);

            log.info("refund.issued id={} invoice={} amount={} transferId={}",
                issued.id().value(), invoice.id().value(),
                command.amount(), transferId.value());

            return new RefundCreatedResult(issued);
        } catch (RuntimeException postCustody) {
            log.error("refund.create-failed-AFTER-custody key={} invoice={} transferId={} — "
                + "claim left PENDING; manual reconciliation required",
                command.idempotencyKey().value(), invoice.id().value(), transferId.value(),
                postCustody);
            throw postCustody;
        }
    }

    private void validateInvoiceRefundable(Invoice invoice, IssueRefundCommand command) {
        if (invoice.status() != InvoiceStatus.PAID) {
            throw new InvoiceNotRefundableException(invoice.id(),
                "invoice status is " + invoice.status() + ", must be PAID");
        }
        BigInteger alreadyRefunded = refunds.sumIssuedNonFailed(invoice.id());
        BigInteger newTotal = alreadyRefunded.add(command.amount());
        if (newTotal.compareTo(invoice.expectedAmount()) > 0) {
            throw new InvoiceNotRefundableException(invoice.id(),
                "refund amount " + command.amount() + " + previously refunded " + alreadyRefunded
                    + " exceeds invoice expected " + invoice.expectedAmount());
        }
    }

    private TransferId sendToCustody(Invoice invoice, IssueRefundCommand command) {
        IdempotencyKey adapterKey = IdempotencyKey.of("refund-" + command.idempotencyKey().value());
        SendTransferCommand cmd = new SendTransferCommand(
            adapterKey,
            command.fromAddress(),
            command.toAddress(),
            invoice.asset(),
            command.amount(),
            command.memo(),
            FeePreference.NORMAL
        );
        return transfers.sendTransfer(cmd);
    }
}
