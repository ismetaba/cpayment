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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Advances a {@link Refund} on Transfer* events. Same shape as
 * {@code UpdatePayoutFromTransferUseCase}; only the type matters at the use case level.
 */
public final class UpdateRefundFromTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateRefundFromTransferUseCase.class);

    private final RefundRepository repo;
    private final RefundMutationGateway gateway;
    private final Clock clock;

    public UpdateRefundFromTransferUseCase(RefundRepository repo,
                                           RefundMutationGateway gateway,
                                           Clock clock) {
        this.repo = Objects.requireNonNull(repo, "repo");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean onBroadcast(CustodyEvent.TransferBroadcast e) {
        return advance(e.id(), "broadcast", before -> switch (before) {
            case IssuedRefund r    -> r.broadcast(e.txHash(), clock.instant());
            case BroadcastRefund r -> dropRedelivery("broadcast", r);
            case ConfirmedRefund r -> dropTerminal("broadcast", r);
            case FailedRefund    r -> dropTerminal("broadcast", r);
        }, RefundEventType.REFUND_BROADCAST);
    }

    public boolean onConfirmed(CustodyEvent.TransferConfirmed e) {
        return advance(e.id(), "confirmed", before -> switch (before) {
            case BroadcastRefund r -> r.confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case IssuedRefund r    -> r.broadcast(e.txHash(), clock.instant())
                                       .confirm(e.confirmations(), e.feeActual(), e.feeAsset(), clock.instant());
            case ConfirmedRefund r -> dropRedelivery("confirmed", r);
            case FailedRefund    r -> dropTerminal("confirmed", r);
        }, RefundEventType.REFUND_CONFIRMED);
    }

    public boolean onFailed(CustodyEvent.TransferFailed e) {
        return advance(e.id(), "failed", before -> switch (before) {
            case IssuedRefund r    -> r.fail(e.reason(), e.message(), clock.instant());
            case BroadcastRefund r -> r.fail(e.reason(), e.message(), clock.instant());
            case FailedRefund r    -> dropRedelivery("failed", r);
            case ConfirmedRefund r -> dropTerminal("failed", r);
        }, RefundEventType.REFUND_FAILED);
    }

    /** Returns true if a refund was found+updated (caller can short-circuit fallback lookups). */
    private boolean advance(TransferId transferId, String op,
                            java.util.function.Function<Refund, Refund> transition,
                            RefundEventType type) {
        Optional<Refund> maybe = repo.findByCustodyTransferId(transferId);
        if (maybe.isEmpty()) return false;
        Refund before = maybe.get();
        Refund next = transition.apply(before);
        if (next == null) return true; // dropped — was handled, just nothing to save
        gateway.apply(next, List.of(RefundEvent.of(type, next)));
        log.info("refund.advanced id={} {} -> {} transferId={}",
            before.id().value(), before.status(), next.status(), transferId.value());
        return true;
    }

    private Refund dropRedelivery(String op, Refund r) {
        log.warn("refund.{}-redelivery refund={} status={}", op, r.id().value(), r.status());
        return null;
    }

    private Refund dropTerminal(String op, Refund r) {
        log.warn("refund.{}-after-terminal refund={} status={}", op, r.id().value(), r.status());
        return null;
    }
}
