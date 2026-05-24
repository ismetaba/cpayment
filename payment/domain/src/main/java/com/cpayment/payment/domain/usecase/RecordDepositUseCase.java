package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceStatus;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.PaymentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class RecordDepositUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordDepositUseCase.class);

    private final InvoiceRepository invoices;
    private final PaymentMetrics metrics;
    private final Clock clock;

    public RecordDepositUseCase(InvoiceRepository invoices, PaymentMetrics metrics, Clock clock) {
        this.invoices = Objects.requireNonNull(invoices, "invoices");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void handle(CustodyEvent.DepositDetected event) {
        Objects.requireNonNull(event, "event");

        Optional<Invoice> match = invoices.findByCustodyAccount(event.account());
        if (match.isEmpty()) {
            metrics.depositOrphan();
            log.warn("deposit.orphan account={} txHash={} amount={} asset={}",
                     event.account().value(),
                     event.txHash(),
                     event.amount(),
                     event.asset().canonical());
            return;
        }

        Invoice invoice = match.get();
        if (invoice.status() != InvoiceStatus.AWAITING_DEPOSIT) {
            metrics.depositDuplicate();
            log.warn("deposit.duplicate invoice={} currentStatus={} txHash={}",
                     invoice.id().value(), invoice.status(), event.txHash());
            return;
        }

        Instant now = clock.instant();
        BigInteger received = event.amount();
        boolean fullyPaid = received.compareTo(invoice.expectedAmount()) >= 0;
        Invoice updated = fullyPaid
            ? invoice.markPaid(event.txHash(), received, now)
            : invoice.markUnderpaid(event.txHash(), received, now);

        invoices.save(updated);
        if (fullyPaid) metrics.depositPaid(invoice.asset());
        else           metrics.depositUnderpaid(invoice.asset());

        log.info("deposit.detected invoice={} merchant={} status={} expected={} received={} txHash={}",
                 invoice.id().value(),
                 invoice.merchantId().value(),
                 updated.status(),
                 invoice.expectedAmount(),
                 received,
                 event.txHash());
    }
}
