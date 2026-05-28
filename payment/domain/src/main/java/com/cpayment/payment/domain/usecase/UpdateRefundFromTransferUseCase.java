package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.BroadcastRefund;
import com.cpayment.payment.domain.model.ConfirmedRefund;
import com.cpayment.payment.domain.model.FailedRefund;
import com.cpayment.payment.domain.model.IssuedRefund;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundEvent;
import com.cpayment.payment.domain.model.RefundEventType;
import com.cpayment.payment.domain.port.RefundMutationGateway;
import com.cpayment.payment.domain.port.RefundRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Advances a {@link Refund} on Transfer* events. Same shape as
 * {@code UpdatePayoutFromTransferUseCase}; the lookup / drop / persist / logging
 * scaffolding is shared via {@link AbstractTransferEventApplier} and only the transition
 * switches and refund-event persistence are specific here. Refunds have no "replaced"
 * transition.
 */
public final class UpdateRefundFromTransferUseCase extends AbstractTransferEventApplier<Refund> {

    private final RefundRepository repo;
    private final RefundMutationGateway gateway;

    public UpdateRefundFromTransferUseCase(RefundRepository repo,
                                           RefundMutationGateway gateway,
                                           Clock clock) {
        super(clock, "refund");
        this.repo = Objects.requireNonNull(repo, "repo");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    protected Optional<Refund> findByTransferId(TransferId transferId) {
        return repo.findByCustodyTransferId(transferId);
    }

    @Override
    protected Object idOf(Refund refund) {
        return refund.id().value();
    }

    @Override
    protected Object statusOf(Refund refund) {
        return refund.status();
    }

    public boolean onBroadcast(CustodyEvent.TransferBroadcast e) {
        return advance(e.id(), "broadcast", before -> switch (before) {
            case IssuedRefund r    -> r.broadcast(e.txHash(), clock.instant());
            case BroadcastRefund r -> dropRedelivery("broadcast", r);
            case ConfirmedRefund r -> dropTerminal("broadcast", r);
            case FailedRefund    r -> dropTerminal("broadcast", r);
        }, persist(RefundEventType.REFUND_BROADCAST));
    }

    public boolean onConfirmed(CustodyEvent.TransferConfirmed e) {
        return advance(e.id(), "confirmed", before -> switch (before) {
            case BroadcastRefund r -> r.confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case IssuedRefund r    -> r.broadcast(e.txHash(), clock.instant())
                                       .confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case ConfirmedRefund r -> dropRedelivery("confirmed", r);
            case FailedRefund    r -> dropTerminal("confirmed", r);
        }, persist(RefundEventType.REFUND_CONFIRMED));
    }

    public boolean onFailed(CustodyEvent.TransferFailed e) {
        return advance(e.id(), "failed", before -> switch (before) {
            case IssuedRefund r    -> r.fail(e.reason(), e.message(), clock.instant());
            case BroadcastRefund r -> r.fail(e.reason(), e.message(), clock.instant());
            case FailedRefund r    -> dropRedelivery("failed", r);
            case ConfirmedRefund r -> dropTerminal("failed", r);
        }, persist(RefundEventType.REFUND_FAILED));
    }

    private java.util.function.Consumer<Refund> persist(RefundEventType type) {
        return next -> gateway.apply(next, List.of(RefundEvent.of(type, next)));
    }
}
