package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.PayoutEventType;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.domain.port.PayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Advances a {@link Payout} when the custody bridge emits a Transfer* event. Idempotent
 * — re-delivery of the same event for a terminal payout (CONFIRMED / FAILED /
 * REPLACED / CANCELLED) is dropped with a warn log; no resave.
 */
public final class UpdatePayoutFromTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdatePayoutFromTransferUseCase.class);

    private final PayoutRepository repo;
    private final PayoutMutationGateway gateway;
    private final Clock clock;

    public UpdatePayoutFromTransferUseCase(PayoutRepository repo,
                                           PayoutMutationGateway gateway,
                                           Clock clock) {
        this.repo = Objects.requireNonNull(repo, "repo");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void onBroadcast(CustodyEvent.TransferBroadcast e) {
        apply(e.id(), payout -> {
            Payout next = payout.markBroadcast(e.txHash(), clock.instant());
            return new Outcome(next, PayoutEventType.PAYOUT_BROADCAST);
        });
    }

    public void onConfirmed(CustodyEvent.TransferConfirmed e) {
        apply(e.id(), payout -> {
            Payout next = payout.markConfirmed(e.txHash(), e.confirmations(),
                e.feeActual(), e.feeAsset(), clock.instant());
            return new Outcome(next, PayoutEventType.PAYOUT_CONFIRMED);
        });
    }

    public void onFailed(CustodyEvent.TransferFailed e) {
        apply(e.id(), payout -> {
            Payout next = payout.markFailed(e.reason(), e.message(), clock.instant());
            return new Outcome(next, PayoutEventType.PAYOUT_FAILED);
        });
    }

    public void onReplaced(CustodyEvent.TransferReplaced e) {
        apply(e.oldId(), payout -> {
            Payout next = payout.markReplaced(e.newId(), clock.instant());
            return new Outcome(next, PayoutEventType.PAYOUT_REPLACED);
        });
    }

    private void apply(TransferId transferId, java.util.function.Function<Payout, Outcome> transition) {
        Optional<Payout> maybe = repo.findByCustodyTransferId(transferId);
        if (maybe.isEmpty()) {
            log.warn("payout.transfer-orphan transferId={} — no local payout found", transferId.value());
            return;
        }
        Payout before = maybe.get();
        if (before.status().isTerminal()) {
            log.warn("payout.transfer-after-terminal payout={} status={} transferId={}",
                before.id().value(), before.status(), transferId.value());
            return;
        }
        Outcome outcome = transition.apply(before);
        gateway.apply(outcome.payout(), List.of(PayoutEvent.of(outcome.type(), outcome.payout())));
        log.info("payout.advanced id={} {} -> {} transferId={}",
            before.id().value(), before.status(), outcome.payout().status(), transferId.value());
    }

    private record Outcome(Payout payout, PayoutEventType type) {}

    // Convenience for tests; ignored in production.
    @SuppressWarnings("unused")
    private static Instant noop() { return Instant.EPOCH; }
}
