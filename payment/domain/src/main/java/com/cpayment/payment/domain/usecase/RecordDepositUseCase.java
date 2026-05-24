package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceStatus;
import com.cpayment.payment.domain.port.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service — reacts to a normalized {@link CustodyEvent.DepositDetected}
 * by locating the corresponding invoice and advancing its status.
 *
 * <h2>Idempotency</h2>
 * <p>Deposit events may be redelivered (RabbitMQ at-least-once). This service is
 * idempotent: if the invoice is already in a non-{@code AWAITING_DEPOSIT} state, the
 * event is ignored with a warning log.
 *
 * <h2>Status decision</h2>
 * <ul>
 *   <li>received &gt;= expected → {@code PAID}</li>
 *   <li>received &lt; expected → {@code UNDERPAID} (merchant decides how to handle)</li>
 * </ul>
 * In a future iteration the threshold may be expressed as a {@code minConfirmations}
 * policy attached to the invoice; for now any detection is treated as final.
 *
 * <h2>Orphan deposits</h2>
 * <p>Deposits arriving at an account that does not correspond to any invoice (direct
 * sends, stale addresses) are logged at WARN and dropped. They are not an error
 * from cpayment's perspective; an operator may reconcile them out-of-band.
 */
public final class RecordDepositUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordDepositUseCase.class);

    private final InvoiceRepository invoices;
    private final Clock clock;

    public RecordDepositUseCase(InvoiceRepository invoices, Clock clock) {
        this.invoices = Objects.requireNonNull(invoices, "invoices");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void handle(CustodyEvent.DepositDetected event) {
        Objects.requireNonNull(event, "event");

        Optional<Invoice> match = invoices.findByCustodyAccount(event.account());
        if (match.isEmpty()) {
            log.warn("deposit.orphan account={} txHash={} amount={} asset={}",
                     event.account().value(),
                     event.txHash(),
                     event.amount(),
                     event.asset().canonical());
            return;
        }

        Invoice invoice = match.get();
        if (invoice.status() != InvoiceStatus.AWAITING_DEPOSIT) {
            log.warn("deposit.duplicate invoice={} currentStatus={} txHash={}",
                     invoice.id().value(), invoice.status(), event.txHash());
            return;
        }

        Instant now = clock.instant();
        BigInteger received = event.amount();
        Invoice updated = received.compareTo(invoice.expectedAmount()) >= 0
            ? invoice.markPaid(event.txHash(), received, now)
            : invoice.markUnderpaid(event.txHash(), received, now);

        invoices.save(updated);

        log.info("deposit.detected invoice={} merchant={} status={} expected={} received={} txHash={}",
                 invoice.id().value(),
                 invoice.merchantId().value(),
                 updated.status(),
                 invoice.expectedAmount(),
                 received,
                 event.txHash());
    }
}
