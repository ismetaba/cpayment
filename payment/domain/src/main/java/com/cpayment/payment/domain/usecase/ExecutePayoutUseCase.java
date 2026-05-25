package com.cpayment.payment.domain.usecase;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferPort;
import com.cpayment.payment.domain.model.CreatePayoutCommand;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutCreatedResult;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.PayoutEventType;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.model.SubmittedPayout;
import com.cpayment.payment.domain.port.PayoutIdempotencyStore;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes a payout: two-phase idempotency claim → cus-server send → save aggregate +
 * emit PAYOUT_SUBMITTED webhook event. The crash-window semantics mirror
 * {@link CreateInvoiceUseCase}:
 *
 * <ul>
 *   <li>Failure BEFORE the custody call → release claim, retry safe.</li>
 *   <li>Failure AFTER custody but before local save → claim STAYS PENDING; the next
 *       retry gets 409 IDEMPOTENCY_IN_PROGRESS and an operator reconciles via the
 *       payout id (the adapter-side TransferId is the bridge back).</li>
 * </ul>
 *
 * <p>The custody adapter's own idempotency layer (CusServerTransferAdapter +
 * IdempotencyStore) provides a second defence: a stale retry that DOES reach
 * cus-server still won't create a duplicate transfer, because the adapter looks up
 * (idempotencyKey, requestHash) before POSTing.
 */
public final class ExecutePayoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecutePayoutUseCase.class);

    private final PayoutIdempotencyStore idempotency;
    private final PayoutMutationGateway gateway;
    private final TransferPort transfers;
    private final Clock clock;

    public ExecutePayoutUseCase(PayoutIdempotencyStore idempotency,
                                PayoutMutationGateway gateway,
                                TransferPort transfers,
                                Clock clock) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PayoutCreatedResult execute(CreatePayoutCommand command) {
        Objects.requireNonNull(command, "command");

        String hash = PayoutRequestHash.of(command);
        Optional<Payout> cached = idempotency.beginClaim(command.idempotencyKey(), hash);
        if (cached.isPresent()) {
            log.info("payout.idempotent-hit key={} payout={}",
                command.idempotencyKey().value(), cached.get().id().value());
            return new PayoutCreatedResult(cached.get());
        }

        PayoutId payoutId = PayoutId.newId();
        TransferId transferId;
        try {
            transferId = sendToCustody(command);
        } catch (RuntimeException preCustody) {
            idempotency.releaseClaim(command.idempotencyKey(), hash);
            log.warn("payout.create-failed-before-custody key={} reason={}",
                command.idempotencyKey().value(), preCustody.getMessage());
            throw preCustody;
        }

        try {
            Instant now = clock.instant();
            SubmittedPayout submitted = SubmittedPayout.fresh(
                payoutId, command.merchantId(), command.asset(),
                command.fromAddress(), command.toAddress(), command.amount(),
                command.memo(), transferId, now);

            gateway.apply(submitted,
                List.of(PayoutEvent.of(PayoutEventType.PAYOUT_SUBMITTED, submitted)));
            idempotency.completeClaim(command.idempotencyKey(), hash, submitted);

            log.info("payout.submitted id={} merchant={} asset={} amount={} transferId={}",
                payoutId.value(), command.merchantId().value(),
                command.asset().canonical(), command.amount(), transferId.value());

            return new PayoutCreatedResult(submitted);
        } catch (RuntimeException postCustody) {
            log.error("payout.create-failed-AFTER-custody key={} payoutId={} transferId={} — "
                + "claim left PENDING; manual reconciliation required",
                command.idempotencyKey().value(), payoutId.value(), transferId.value(),
                postCustody);
            throw postCustody;
        }
    }

    private TransferId sendToCustody(CreatePayoutCommand command) {
        // Adapter-side idempotency uses the same client-supplied key, with a payout
        // prefix to avoid colliding with invoice claims that share the namespace.
        IdempotencyKey adapterKey = IdempotencyKey.of("payout-" + command.idempotencyKey().value());
        SendTransferCommand cmd = new SendTransferCommand(
            adapterKey,
            command.fromAddress(),
            command.toAddress(),
            command.asset(),
            command.amount(),
            command.memo(),
            com.cpayment.custody.domain.model.FeePreference.NORMAL
        );
        return transfers.sendTransfer(cmd);
    }
}
