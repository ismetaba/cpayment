package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.model.Account;
import com.cpayment.custody.domain.model.CreateAccountCommand;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.custody.domain.port.AccountPort;
import com.cpayment.payment.domain.model.CreateInvoiceCommand;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceCreatedResult;
import com.cpayment.payment.domain.model.InvoiceEvent;
import com.cpayment.payment.domain.model.InvoiceEventType;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import com.cpayment.payment.domain.port.InvoiceMutationGateway;
import com.cpayment.payment.domain.port.MerchantWalletResolver;
import com.cpayment.payment.domain.port.PaymentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Application service — orchestrates invoice creation across the custody and payment
 * bounded contexts using a two-phase idempotency claim that closes the orphan-account
 * crash window.
 *
 * <h2>Failure semantics</h2>
 * <ul>
 *   <li><b>Failure BEFORE custody.</b> The PENDING claim is released; retries proceed.
 *       No orphan possible because no custody call was made.</li>
 *   <li><b>Failure AFTER custody, BEFORE save.</b> The PENDING claim is NOT released.
 *       Subsequent retries get {@code IdempotencyInProgressException} (HTTP 409) and
 *       an operator can reconcile via the {@code invoice-<uuid>} label on cus-server.
 *       Strictly better than the previous design where retry would create a second orphan.</li>
 *   <li><b>Success.</b> Claim transitions PENDING → COMPLETED; future retries return
 *       the cached invoice.</li>
 * </ul>
 */
public final class CreateInvoiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateInvoiceUseCase.class);

    private final InvoiceMutationGateway gateway;
    private final InvoiceIdempotencyStore idempotency;
    private final MerchantWalletResolver merchantWallets;
    private final AccountPort custodyAccounts;
    private final PaymentMetrics metrics;
    private final Clock clock;

    public CreateInvoiceUseCase(InvoiceMutationGateway gateway,
                                InvoiceIdempotencyStore idempotency,
                                MerchantWalletResolver merchantWallets,
                                AccountPort custodyAccounts,
                                PaymentMetrics metrics,
                                Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.merchantWallets = Objects.requireNonNull(merchantWallets, "merchantWallets");
        this.custodyAccounts = Objects.requireNonNull(custodyAccounts, "custodyAccounts");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public InvoiceCreatedResult execute(CreateInvoiceCommand command) {
        Objects.requireNonNull(command, "command");

        String requestHash = InvoiceRequestHash.of(command);
        Optional<Invoice> cached = idempotency.beginClaim(command.idempotencyKey(), requestHash);
        if (cached.isPresent()) {
            metrics.invoiceIdempotentHit();
            log.info("invoice.idempotent-hit key={} invoice={}",
                     command.idempotencyKey().value(), cached.get().id().value());
            return new InvoiceCreatedResult(cached.get());
        }

        // PENDING claim held. We MUST either complete or release before returning.

        Account account;
        InvoiceId invoiceId = InvoiceId.newId();
        try {
            WalletId wallet = merchantWallets.resolveDepositWallet(
                command.merchantId(), command.asset().network());

            account = custodyAccounts.createAccount(new CreateAccountCommand(
                wallet,
                command.asset().network(),
                "invoice-" + invoiceId.value(),
                Set.of(command.asset().symbol())
            ));
        } catch (RuntimeException preCustody) {
            // No custody side effect → safe to release the claim.
            idempotency.releaseClaim(command.idempotencyKey(), requestHash);
            log.warn("invoice.create-failed-before-custody key={} reason={}",
                     command.idempotencyKey().value(), preCustody.getMessage());
            throw preCustody;
        }

        // From here on the custody side effect is committed. Failures below leave the
        // claim PENDING so a retry does NOT cause a duplicate cus-server account.
        try {
            Instant now = clock.instant();
            Invoice invoice = Invoice.newlyCreated(
                invoiceId,
                command.merchantId(),
                command.asset(),
                command.expectedAmount(),
                command.minConfirmations(),
                account.id(),
                account.address(),
                now
            );

            gateway.apply(invoice, List.of(InvoiceEvent.of(InvoiceEventType.INVOICE_CREATED, invoice)));
            idempotency.completeClaim(command.idempotencyKey(), requestHash, invoice);
            metrics.invoiceCreated(command.asset());

            log.info("invoice.created id={} merchant={} asset={} expected={} address={} account={}",
                     invoiceId.value(),
                     command.merchantId().value(),
                     command.asset().canonical(),
                     command.expectedAmount(),
                     account.address(),
                     account.id().value());

            return new InvoiceCreatedResult(invoice);

        } catch (RuntimeException postCustody) {
            log.error("invoice.create-failed-AFTER-custody key={} invoiceId={} custodyAccount={} "
                    + "label=invoice-{} — claim left PENDING; manual reconciliation required",
                command.idempotencyKey().value(), invoiceId.value(), account.id().value(),
                invoiceId.value(), postCustody);
            throw postCustody;
        }
    }
}
