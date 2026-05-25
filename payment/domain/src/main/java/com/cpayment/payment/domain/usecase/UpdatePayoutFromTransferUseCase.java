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
 * Advances a {@link Payout} on Transfer* events.
 *
 * <p>Each on-method returns {@code boolean}: {@code true} if a payout matched the
 * inbound {@link TransferId} (handled — whether updated or deliberately dropped),
 * {@code false} if no payout matched. The dispatcher uses the return value to
 * decide whether to fall through to refund routing or to log the event as orphan.
 *
 * <p>The exhaustive sealed-type switch makes invalid transitions impossible:
 * trying to {@code confirm} a {@link SubmittedPayout} (not yet broadcast) won't
 * even compile, because {@link SubmittedPayout} has no {@code confirm} method.
 * Re-delivery of an event whose payout is already terminal is logged and dropped.
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

    public boolean onBroadcast(CustodyEvent.TransferBroadcast e) {
        return advance(e.id(), "broadcast", PayoutEventType.PAYOUT_BROADCAST, before -> switch (before) {
            case SubmittedPayout s -> s.broadcast(e.txHash(), clock.instant());
            case BroadcastPayout b -> dropRedelivery("broadcast", b);
            case ConfirmedPayout c -> dropTerminal("broadcast", c);
            case FailedPayout    f -> dropTerminal("broadcast", f);
            case ReplacedPayout  r -> dropTerminal("broadcast", r);
            case CancelledPayout x -> dropTerminal("broadcast", x);
        });
    }

    public boolean onConfirmed(CustodyEvent.TransferConfirmed e) {
        return advance(e.id(), "confirmed", PayoutEventType.PAYOUT_CONFIRMED, before -> switch (before) {
            case BroadcastPayout b -> b.confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case SubmittedPayout s -> s.broadcast(e.txHash(), clock.instant())
                .confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case ConfirmedPayout c -> dropRedelivery("confirmed", c);
            case FailedPayout    f -> dropTerminal("confirmed", f);
            case ReplacedPayout  r -> dropTerminal("confirmed", r);
            case CancelledPayout x -> dropTerminal("confirmed", x);
        });
    }

    public boolean onFailed(CustodyEvent.TransferFailed e) {
        return advance(e.id(), "failed", PayoutEventType.PAYOUT_FAILED, before -> switch (before) {
            case SubmittedPayout s -> s.fail(e.reason(), e.message(), clock.instant());
            case BroadcastPayout b -> b.fail(e.reason(), e.message(), clock.instant());
            case FailedPayout    f -> dropRedelivery("failed", f);
            case ConfirmedPayout c -> dropTerminal("failed", c);
            case ReplacedPayout  r -> dropTerminal("failed", r);
            case CancelledPayout x -> dropTerminal("failed", x);
        });
    }

    public boolean onReplaced(CustodyEvent.TransferReplaced e) {
        return advance(e.oldId(), "replaced", PayoutEventType.PAYOUT_REPLACED, before -> switch (before) {
            case SubmittedPayout s -> s.replaceWith(e.newId(), clock.instant());
            case BroadcastPayout b -> b.replaceWith(e.newId(), clock.instant());
            case ReplacedPayout  r -> dropRedelivery("replaced", r);
            case ConfirmedPayout c -> dropTerminal("replaced", c);
            case FailedPayout    f -> dropTerminal("replaced", f);
            case CancelledPayout x -> dropTerminal("replaced", x);
        });
    }

    private boolean advance(TransferId transferId, String op, PayoutEventType type,
                            java.util.function.Function<Payout, Payout> transition) {
        Optional<Payout> maybe = repo.findByCustodyTransferId(transferId);
        if (maybe.isEmpty()) return false;
        Payout before = maybe.get();
        Payout next = transition.apply(before);
        if (next == null) return true; // dropped — was a payout match, just nothing to save
        gateway.apply(next, List.of(PayoutEvent.of(type, next)));
        log.info("payout.advanced id={} {} -> {} transferId={}",
            before.id().value(), before.status(), next.status(), transferId.value());
        return true;
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
