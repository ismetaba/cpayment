package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.exception.PayoutNotCancellableException;
import com.cpayment.payment.domain.exception.PayoutNotFoundException;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.PayoutEventType;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.model.PayoutStatus;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.domain.port.PayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Operator-cancel a payout that has not yet been broadcast.
 *
 * <h2>Cancellability matrix</h2>
 * <ul>
 *   <li>{@code REQUESTED} — never observable externally (the create use case
 *       transitions REQUESTED → SUBMITTED inside the same call). Allowed for
 *       completeness so future "draft payout" flows fit without a new branch.</li>
 *   <li>{@code SUBMITTED} — allowed. The transfer sits in cus-server's policy
 *       queue; cpayment marks the local aggregate CANCELLED and emits
 *       PAYOUT_CANCELLED. cus-server has no cancel endpoint yet, so the upstream
 *       tx may still broadcast — operators reconcile via the resulting
 *       TransferBroadcast/Confirmed event (the local CANCELLED is treated as a
 *       business intent, not a hard on-chain guarantee).</li>
 *   <li>All other states — rejected with {@link PayoutNotCancellableException}.</li>
 * </ul>
 *
 * <p>Cancelling a CANCELLED payout is a no-op (idempotent). Cancelling a
 * BROADCAST/CONFIRMED/FAILED/REPLACED payout is rejected — those are terminal
 * outcomes.
 */
public final class CancelPayoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelPayoutUseCase.class);

    private static final Set<PayoutStatus> CANCELLABLE =
        Set.of(PayoutStatus.REQUESTED, PayoutStatus.SUBMITTED);

    private final PayoutRepository payouts;
    private final PayoutMutationGateway gateway;
    private final Clock clock;

    public CancelPayoutUseCase(PayoutRepository payouts,
                               PayoutMutationGateway gateway,
                               Clock clock) {
        this.payouts = Objects.requireNonNull(payouts, "payouts");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Payout execute(PayoutId id) {
        Objects.requireNonNull(id, "id");

        Payout current = payouts.findById(id).orElseThrow(() -> new PayoutNotFoundException(id));

        if (current.status() == PayoutStatus.CANCELLED) {
            return current; // idempotent
        }
        if (!CANCELLABLE.contains(current.status())) {
            throw new PayoutNotCancellableException(id, current.status());
        }

        Payout cancelled = current.markCancelled(clock.instant());
        gateway.apply(cancelled,
            List.of(PayoutEvent.of(PayoutEventType.PAYOUT_CANCELLED, cancelled)));

        log.info("payout.cancelled id={} previousStatus={} merchant={}",
            id.value(), current.status(), current.merchantId().value());
        return cancelled;
    }
}
