package com.cpayment.payment.domain.usecase;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.Account;
import com.cpayment.custody.domain.model.CreateAccountCommand;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.custody.domain.port.AccountPort;
import com.cpayment.payment.domain.model.CreateInvoiceCommand;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceCreatedResult;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.MerchantWalletResolver;
import com.cpayment.payment.domain.port.PaymentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Application service — orchestrates invoice creation across the custody and payment
 * bounded contexts.
 *
 * <h2>Idempotency contract</h2>
 * <p>Every call must carry an {@link IdempotencyKey}. The use case consults
 * {@link InvoiceIdempotencyStore} <em>before</em> any side effects:
 * <ul>
 *   <li>Same key + same hash → return cached invoice; no custody call, no DB write.</li>
 *   <li>Same key + different hash → idempotency conflict (thrown by the store).</li>
 *   <li>New key → run the full flow, then persist the (key, hash, invoice) mapping.</li>
 * </ul>
 * This guarantees that an HTTP-level retry by the client never causes a second custody
 * account to be provisioned — the chief risk that motivated this design.
 *
 * <h2>Limitations</h2>
 * <p>If the process crashes <em>between</em> the custody call and the local persistence
 * step, the next retry will provision a second custody account because we never recorded
 * the in-flight intent. A persistent outbox would close this window; for the first cut
 * we accept the small leak and document it. Production must replace the in-memory
 * store with one that persists the intent <em>before</em> the custody call.
 *
 * <h2>Threading</h2>
 * <p>Stateless. All collaborators are injected via the constructor and must be
 * thread-safe themselves (every implementation in this codebase is).
 */
public final class CreateInvoiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateInvoiceUseCase.class);

    private final InvoiceRepository invoices;
    private final InvoiceIdempotencyStore idempotency;
    private final MerchantWalletResolver merchantWallets;
    private final AccountPort custodyAccounts;
    private final PaymentMetrics metrics;
    private final Clock clock;

    public CreateInvoiceUseCase(InvoiceRepository invoices,
                                InvoiceIdempotencyStore idempotency,
                                MerchantWalletResolver merchantWallets,
                                AccountPort custodyAccounts,
                                PaymentMetrics metrics,
                                Clock clock) {
        this.invoices = Objects.requireNonNull(invoices, "invoices");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.merchantWallets = Objects.requireNonNull(merchantWallets, "merchantWallets");
        this.custodyAccounts = Objects.requireNonNull(custodyAccounts, "custodyAccounts");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public InvoiceCreatedResult execute(CreateInvoiceCommand command) {
        Objects.requireNonNull(command, "command");

        String requestHash = RequestHash.of(command);
        Optional<Invoice> cached = idempotency.findExisting(command.idempotencyKey(), requestHash);
        if (cached.isPresent()) {
            metrics.invoiceIdempotentHit();
            log.info("invoice.idempotent-hit key={} invoice={}",
                     command.idempotencyKey().value(), cached.get().id().value());
            return new InvoiceCreatedResult(cached.get());
        }

        WalletId wallet = merchantWallets.resolveDepositWallet(
            command.merchantId(), command.asset().network());

        InvoiceId invoiceId = InvoiceId.newId();

        Account account = custodyAccounts.createAccount(new CreateAccountCommand(
            wallet,
            command.asset().network(),
            "invoice-" + invoiceId.value(),
            Set.of(command.asset().symbol())
        ));

        Instant now = clock.instant();
        Invoice invoice = Invoice.newlyCreated(
            invoiceId,
            command.merchantId(),
            command.asset(),
            command.expectedAmount(),
            account.id(),
            account.address(),
            now
        );

        invoices.save(invoice);
        idempotency.record(command.idempotencyKey(), requestHash, invoice);
        metrics.invoiceCreated(command.asset());

        log.info("invoice.created id={} merchant={} asset={} expected={} address={} account={}",
                 invoiceId.value(),
                 command.merchantId().value(),
                 command.asset().canonical(),
                 command.expectedAmount(),
                 account.address(),
                 account.id().value());

        return new InvoiceCreatedResult(invoice);
    }
}
