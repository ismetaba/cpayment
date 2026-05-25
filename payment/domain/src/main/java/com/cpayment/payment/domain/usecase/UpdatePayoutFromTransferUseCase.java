package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.BroadcastPayout;
import com.cpayment.payment.domain.model.CancelledPayout;
import com.cpayment.payment.domain.model.ConfirmedPayout;
import com.cpayment.payment.domain.model.FailedPayout;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.PayoutEventType;
import com.cpayment.payment.domain.model.ReplacedPayout;
import com.cpayment.payment.domain.model.SubmittedPayout;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.domain.port.PayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Advances a {@link Payout} when the custody bridge emits a Transfer* event.
 *
 * <p>The exhaustive sealed-type switch makes invalid transitions impossible:
 * trying to {@code confirm} a {@link SubmittedPayout} (not yet broadcast) won't
 * even compile, because {@link SubmittedPayout} has no {@code confirm} method.
 * Re-delivery of an event whose payout is already terminal — or in a state where
 * the transition makes no sense — is logged and dropped without resaving.
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
        Optional<Payout> maybe = lookup(e.id(), "broadcast");
        if (maybe.isEmpty()) return;
        Payout before = maybe.get();
        Payout next = switch (before) {
            case SubmittedPayout s -> s.broadcast(e.txHash(), clock.instant());
            case BroadcastPayout b -> dropRedelivery("broadcast", b);
            case ConfirmedPayout c -> dropTerminal("broadcast", c);
            case FailedPayout    f -> dropTerminal("broadcast", f);
            case ReplacedPayout  r -> dropTerminal("broadcast", r);
            case CancelledPayout x -> dropTerminal("broadcast", x);
        };
        emit(before, next, PayoutEventType.PAYOUT_BROADCAST, e.id());
    }

    public void onConfirmed(CustodyEvent.TransferConfirmed e) {
        Optional<Payout> maybe = lookup(e.id(), "confirmed");
        if (maybe.isEmpty()) return;
        Payout before = maybe.get();
        Payout next = switch (before) {
            case BroadcastPayout b -> b.confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            // Fast-finality chains may publish confirm without a prior broadcast event.
            case SubmittedPayout s -> s.broadcast(e.txHash(), clock.instant())
                .confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case ConfirmedPayout c -> dropRedelivery("confirmed", c);
            case FailedPayout    f -> dropTerminal("confirmed", f);
            case ReplacedPayout  r -> dropTerminal("confirmed", r);
            case CancelledPayout x -> dropTerminal("confirmed", x);
        };
        emit(before, next, PayoutEventType.PAYOUT_CONFIRMED, e.id());
    }

    public void onFailed(CustodyEvent.TransferFailed e) {
        Optional<Payout> maybe = lookup(e.id(), "failed");
        if (maybe.isEmpty()) return;
        Payout before = maybe.get();
        Payout next = switch (before) {
            case SubmittedPayout s -> s.fail(e.reason(), e.message(), clock.instant());
            case BroadcastPayout b -> b.fail(e.reason(), e.message(), clock.instant());
            case FailedPayout    f -> dropRedelivery("failed", f);
            case ConfirmedPayout c -> dropTerminal("failed", c);
            case ReplacedPayout  r -> dropTerminal("failed", r);
            case CancelledPayout x -> dropTerminal("failed", x);
        };
        emit(before, next, PayoutEventType.PAYOUT_FAILED, e.id());
    }

    public void onReplaced(CustodyEvent.TransferReplaced e) {
        Optional<Payout> maybe = lookup(e.oldId(), "replaced");
        if (maybe.isEmpty()) return;
        Payout before = maybe.get();
        Payout next = switch (before) {
            case SubmittedPayout s -> s.replaceWith(e.newId(), clock.instant());
            case BroadcastPayout b -> b.replaceWith(e.newId(), clock.instant());
            case ReplacedPayout  r -> dropRedelivery("replaced", r);
            case ConfirmedPayout c -> dropTerminal("replaced", c);
            case FailedPayout    f -> dropTerminal("replaced", f);
            case CancelledPayout x -> dropTerminal("replaced", x);
        };
        emit(before, next, PayoutEventType.PAYOUT_REPLACED, e.oldId());
    }

    private Optional<Payout> lookup(TransferId transferId, String op) {
        Optional<Payout> maybe = repo.findByCustodyTransferId(transferId);
        if (maybe.isEmpty()) {
            log.warn("payout.transfer-orphan op={} transferId={} — no local payout found",
                op, transferId.value());
        }
        return maybe;
    }

    private void emit(Payout before, Payout next, PayoutEventType type, TransferId transferId) {
        if (next == null) return; // re-delivery or unreachable branch — already logged
        gateway.apply(next, List.of(PayoutEvent.of(type, next)));
        log.info("payout.advanced id={} {} -> {} transferId={}",
            before.id().value(), before.status(), next.status(), transferId.value());
    }

    private Payout dropRedelivery(String op, Payout p) {
        log.warn("payout.{}-redelivery payout={} status={}", op, p.id().value(), p.status());
        return null;
    }

    private Payout dropTerminal(String op, Payout p) {
        log.warn("payout.{}-after-terminal payout={} status={}", op, p.id().value(), p.status());
        return null;
    }
}
