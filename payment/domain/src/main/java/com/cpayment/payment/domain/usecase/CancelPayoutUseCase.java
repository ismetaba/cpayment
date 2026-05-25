package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.exception.PayoutNotCancellableException;
import com.cpayment.payment.domain.exception.PayoutNotFoundException;
import com.cpayment.payment.domain.model.BroadcastPayout;
import com.cpayment.payment.domain.model.CancelledPayout;
import com.cpayment.payment.domain.model.ConfirmedPayout;
import com.cpayment.payment.domain.model.FailedPayout;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.PayoutEventType;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.model.ReplacedPayout;
import com.cpayment.payment.domain.model.SubmittedPayout;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.domain.port.PayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Operator-cancel a payout that has not yet been broadcast.
 *
 * <p>Only {@link SubmittedPayout} is cancellable. Cancelling an already-{@link CancelledPayout}
 * is a no-op (idempotent). Every other variant rejects with
 * {@link PayoutNotCancellableException} → 422.
 *
 * <p>Cus-server has no native cancel endpoint yet, so this is a "local intent" mark:
 * if cus-server has already broadcast the underlying transfer, the subsequent
 * TransferBroadcast/Confirmed event will reconcile the on-chain outcome.
 */
public final class CancelPayoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelPayoutUseCase.class);

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

        return switch (current) {
            case SubmittedPayout s -> cancel(s);
            case CancelledPayout c -> c;  // idempotent
            case BroadcastPayout b -> throw new PayoutNotCancellableException(id, b.status());
            case ConfirmedPayout c -> throw new PayoutNotCancellableException(id, c.status());
            case FailedPayout    f -> throw new PayoutNotCancellableException(id, f.status());
            case ReplacedPayout  r -> throw new PayoutNotCancellableException(id, r.status());
        };
    }

    private CancelledPayout cancel(SubmittedPayout submitted) {
        CancelledPayout cancelled = submitted.cancel(clock.instant());
        gateway.apply(cancelled,
            List.of(PayoutEvent.of(PayoutEventType.PAYOUT_CANCELLED, cancelled)));
        log.info("payout.cancelled id={} merchant={}",
            cancelled.id().value(), cancelled.merchantId().value());
        return cancelled;
    }
}
