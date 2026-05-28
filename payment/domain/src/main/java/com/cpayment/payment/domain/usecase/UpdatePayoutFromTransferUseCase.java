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

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Advances a {@link Payout} on Transfer* events. The lookup / drop / persist / logging
 * scaffolding lives in {@link AbstractTransferEventApplier}; this class supplies only the
 * per-variant transition switches and the payout event persistence.
 *
 * <p>Each on-method returns {@code boolean}: {@code true} if a payout matched the inbound
 * {@link TransferId} (handled — whether updated or deliberately dropped), {@code false} if
 * no payout matched. The dispatcher uses the return value to decide whether to fall
 * through to refund routing or to log the event as orphan.
 *
 * <p>The exhaustive sealed-type switch makes invalid transitions impossible: trying to
 * {@code confirm} a {@link SubmittedPayout} (not yet broadcast) won't even compile,
 * because {@link SubmittedPayout} has no {@code confirm} method. Re-delivery of an event
 * whose payout is already terminal is logged and dropped.
 */
public final class UpdatePayoutFromTransferUseCase extends AbstractTransferEventApplier<Payout> {

    private final PayoutRepository repo;
    private final PayoutMutationGateway gateway;

    public UpdatePayoutFromTransferUseCase(PayoutRepository repo,
                                           PayoutMutationGateway gateway,
                                           Clock clock) {
        super(clock, "payout");
        this.repo = Objects.requireNonNull(repo, "repo");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    protected Optional<Payout> findByTransferId(TransferId transferId) {
        return repo.findByCustodyTransferId(transferId);
    }

    @Override
    protected Object idOf(Payout payout) {
        return payout.id().value();
    }

    @Override
    protected Object statusOf(Payout payout) {
        return payout.status();
    }

    public boolean onBroadcast(CustodyEvent.TransferBroadcast e) {
        return advance(e.id(), "broadcast", before -> switch (before) {
            case SubmittedPayout s -> s.broadcast(e.txHash(), clock.instant());
            case BroadcastPayout b -> dropRedelivery("broadcast", b);
            case ConfirmedPayout c -> dropTerminal("broadcast", c);
            case FailedPayout    f -> dropTerminal("broadcast", f);
            case ReplacedPayout  r -> dropTerminal("broadcast", r);
            case CancelledPayout x -> dropTerminal("broadcast", x);
        }, persist(PayoutEventType.PAYOUT_BROADCAST));
    }

    public boolean onConfirmed(CustodyEvent.TransferConfirmed e) {
        return advance(e.id(), "confirmed", before -> switch (before) {
            case BroadcastPayout b -> b.confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case SubmittedPayout s -> s.broadcast(e.txHash(), clock.instant())
                .confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case ConfirmedPayout c -> dropRedelivery("confirmed", c);
            case FailedPayout    f -> dropTerminal("confirmed", f);
            case ReplacedPayout  r -> dropTerminal("confirmed", r);
            case CancelledPayout x -> dropTerminal("confirmed", x);
        }, persist(PayoutEventType.PAYOUT_CONFIRMED));
    }

    public boolean onFailed(CustodyEvent.TransferFailed e) {
        return advance(e.id(), "failed", before -> switch (before) {
            case SubmittedPayout s -> s.fail(e.reason(), e.message(), clock.instant());
            case BroadcastPayout b -> b.fail(e.reason(), e.message(), clock.instant());
            case FailedPayout    f -> dropRedelivery("failed", f);
            case ConfirmedPayout c -> dropTerminal("failed", c);
            case ReplacedPayout  r -> dropTerminal("failed", r);
            case CancelledPayout x -> dropTerminal("failed", x);
        }, persist(PayoutEventType.PAYOUT_FAILED));
    }

    public boolean onReplaced(CustodyEvent.TransferReplaced e) {
        return advance(e.oldId(), "replaced", before -> switch (before) {
            case SubmittedPayout s -> s.replaceWith(e.newId(), clock.instant());
            case BroadcastPayout b -> b.replaceWith(e.newId(), clock.instant());
            case ReplacedPayout  r -> dropRedelivery("replaced", r);
            case ConfirmedPayout c -> dropTerminal("replaced", c);
            case FailedPayout    f -> dropTerminal("replaced", f);
            case CancelledPayout x -> dropTerminal("replaced", x);
        }, persist(PayoutEventType.PAYOUT_REPLACED));
    }

    private java.util.function.Consumer<Payout> persist(PayoutEventType type) {
        return next -> gateway.apply(next, List.of(PayoutEvent.of(type, next)));
    }
}
