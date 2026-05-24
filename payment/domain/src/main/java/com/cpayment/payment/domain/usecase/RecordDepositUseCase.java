package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.PaymentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Reacts to {@link CustodyEvent.DepositDetected} and {@link CustodyEvent.DepositConfirmed}
 * by locating the corresponding invoice and applying state transitions per the
 * confirmation depth recorded on each invoice.
 *
 * <h2>Decision rules</h2>
 * <ul>
 *   <li>amount &lt; expected → {@code UNDERPAID} (regardless of confirmations).</li>
 *   <li>confirmations &lt; minConfirmations → {@code DETECTED} (still waiting).</li>
 *   <li>confirmations ≥ minConfirmations → {@code PAID}.</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * Re-delivery of the same event for an invoice already in a terminal state (PAID,
 * UNDERPAID, EXPIRED, CANCELLED) is ignored. A DETECTED → DETECTED no-progress event
 * is also dropped to avoid churn in the metrics and persistence.
 */
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

    public void onDetected(CustodyEvent.DepositDetected e) {
        Objects.requireNonNull(e, "event");
        applyDeposit(e.account(), e.txHash(), e.amount(), e.confirmationsSoFar(), e.asset());
    }

    public void onConfirmed(CustodyEvent.DepositConfirmed e) {
        Objects.requireNonNull(e, "event");
        applyDeposit(e.account(), e.txHash(), e.amount(), e.confirmations(), e.asset());
    }

    private void applyDeposit(AccountId account, String txHash, BigInteger amount,
                              int confirmations, AssetId asset) {
        Optional<Invoice> match = invoices.findByCustodyAccount(account);
        if (match.isEmpty()) {
            metrics.depositOrphan();
            log.warn("deposit.orphan account={} txHash={} amount={} asset={}",
                     account.value(), txHash, amount, asset.canonical());
            return;
        }

        Invoice invoice = match.get();
        if (invoice.status().isTerminal()) {
            metrics.depositDuplicate();
            log.warn("deposit.duplicate-after-terminal invoice={} currentStatus={} txHash={}",
                     invoice.id().value(), invoice.status(), txHash);
            return;
        }

        Instant now = clock.instant();
        Invoice updated = decide(invoice, txHash, amount, confirmations, now);

        boolean statusUnchanged = updated.status() == invoice.status();
        boolean depthUnchanged = updated.receivedConfirmations().equals(invoice.receivedConfirmations());
        if (statusUnchanged && depthUnchanged) {
            metrics.depositDuplicate();
            log.debug("deposit.no-progress invoice={} status={} conf={}",
                      invoice.id().value(), invoice.status(), confirmations);
            return;
        }

        invoices.save(updated);
        recordMetric(invoice, updated);

        log.info("deposit.progress invoice={} merchant={} status={}->{} expected={} received={} conf={}/{} txHash={}",
                 invoice.id().value(), invoice.merchantId().value(),
                 invoice.status(), updated.status(),
                 invoice.expectedAmount(), amount,
                 confirmations, invoice.minConfirmations(), txHash);
    }

    private Invoice decide(Invoice invoice, String txHash, BigInteger amount,
                           int confirmations, Instant now) {
        if (amount.compareTo(invoice.expectedAmount()) < 0) {
            return invoice.markUnderpaid(txHash, amount, confirmations, now);
        }
        if (confirmations >= invoice.minConfirmations()) {
            return invoice.markPaid(txHash, amount, confirmations, now);
        }
        return invoice.markDetected(txHash, amount, confirmations, now);
    }

    private void recordMetric(Invoice before, Invoice after) {
        if (before.status() == after.status()) return;
        switch (after.status()) {
            case PAID      -> metrics.depositPaid(after.asset());
            case UNDERPAID -> metrics.depositUnderpaid(after.asset());
            default        -> { /* DETECTED has no dedicated counter yet. */ }
        }
    }
}
