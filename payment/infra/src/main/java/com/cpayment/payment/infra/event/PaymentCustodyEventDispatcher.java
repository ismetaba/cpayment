package com.cpayment.payment.infra.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.CustodyEventSink;
import com.cpayment.payment.domain.port.PayoutRepository;
import com.cpayment.payment.domain.port.RefundRepository;
import com.cpayment.payment.domain.usecase.RecordDepositUseCase;
import com.cpayment.payment.domain.usecase.UpdatePayoutFromTransferUseCase;
import com.cpayment.payment.domain.usecase.UpdateRefundFromTransferUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes normalized {@link CustodyEvent}s to domain use cases.
 *
 * <h2>Resource resolution for Transfer* events</h2>
 * <p>cus-server only knows about {@link TransferId}s — it has no notion of "payout" vs
 * "refund". cpayment uses the same id for both, so the dispatcher checks the payout
 * repository first; if the transfer isn't a known payout, it tries the refund
 * repository. The lookup cost is tiny (PK on a UUID column) and removes the need
 * to embed cpayment-side type info in the custody side.
 *
 * <p>If both lookups miss the event is logged + counted as orphan; this is expected
 * for transfers initiated outside cpayment (operator-driven sweeps, gas top-ups).
 */
@Component
public class PaymentCustodyEventDispatcher implements CustodyEventSink {

    private static final Logger log = LoggerFactory.getLogger(PaymentCustodyEventDispatcher.class);

    private final RecordDepositUseCase recordDeposit;
    private final UpdatePayoutFromTransferUseCase updatePayout;
    private final UpdateRefundFromTransferUseCase updateRefund;
    private final PayoutRepository payouts;
    private final RefundRepository refunds;
    private final MeterRegistry meters;

    public PaymentCustodyEventDispatcher(RecordDepositUseCase recordDeposit,
                                         UpdatePayoutFromTransferUseCase updatePayout,
                                         UpdateRefundFromTransferUseCase updateRefund,
                                         PayoutRepository payouts,
                                         RefundRepository refunds,
                                         MeterRegistry meters) {
        this.recordDeposit = recordDeposit;
        this.updatePayout = updatePayout;
        this.updateRefund = updateRefund;
        this.payouts = payouts;
        this.refunds = refunds;
        this.meters = meters;
    }

    @Override
    public void accept(CustodyEventEnvelope envelope) {
        switch (envelope.event()) {
            case CustodyEvent.DepositDetected e   -> recordDeposit.onDetected(e);
            case CustodyEvent.DepositConfirmed e  -> recordDeposit.onConfirmed(e);
            case CustodyEvent.TransferBroadcast e -> routeBroadcast(e, envelope);
            case CustodyEvent.TransferConfirmed e -> routeConfirmed(e, envelope);
            case CustodyEvent.TransferFailed e    -> routeFailed(e, envelope);
            case CustodyEvent.TransferReplaced e  -> routeReplaced(e, envelope);
            case CustodyEvent.ApprovalPending e   -> noteUnhandled("ApprovalPending", envelope);
            case CustodyEvent.ApprovalGranted e   -> noteUnhandled("ApprovalGranted", envelope);
            case CustodyEvent.ApprovalRejected e  -> noteUnhandled("ApprovalRejected", envelope);
            case CustodyEvent.BalanceStale e      -> noteUnhandled("BalanceStale", envelope);
        }
    }

    private void routeBroadcast(CustodyEvent.TransferBroadcast e, CustodyEventEnvelope env) {
        if (isPayout(e.id())) { updatePayout.onBroadcast(e); return; }
        if (updateRefund.onBroadcast(e)) return;
        orphan("TransferBroadcast", e.id(), env);
    }

    private void routeConfirmed(CustodyEvent.TransferConfirmed e, CustodyEventEnvelope env) {
        if (isPayout(e.id())) { updatePayout.onConfirmed(e); return; }
        if (updateRefund.onConfirmed(e)) return;
        orphan("TransferConfirmed", e.id(), env);
    }

    private void routeFailed(CustodyEvent.TransferFailed e, CustodyEventEnvelope env) {
        if (isPayout(e.id())) { updatePayout.onFailed(e); return; }
        if (updateRefund.onFailed(e)) return;
        orphan("TransferFailed", e.id(), env);
    }

    private void routeReplaced(CustodyEvent.TransferReplaced e, CustodyEventEnvelope env) {
        if (isPayout(e.oldId())) { updatePayout.onReplaced(e); return; }
        // Refund flow doesn't expose RBF — replacement against an in-flight refund is
        // operationally unusual; we log + drop until that path is needed.
        orphan("TransferReplaced", e.oldId(), env);
    }

    private boolean isPayout(TransferId id) {
        return payouts.findByCustodyTransferId(id).isPresent();
    }

    private void orphan(String type, TransferId transferId, CustodyEventEnvelope env) {
        Counter.builder("cpayment.custody.event.orphan")
            .tags(Tags.of("type", type))
            .register(meters).increment();
        log.warn("event.orphan type={} transferId={} providerEventId={}",
            type, transferId.value(), env.providerEventId());
    }

    private void noteUnhandled(String typeName, CustodyEventEnvelope envelope) {
        Counter.builder("cpayment.custody.event.unhandled")
            .tags(Tags.of("type", typeName))
            .register(meters).increment();
        log.debug("event.unhandled type={} provider={} providerEventId={}",
            typeName, envelope.providerName(), envelope.providerEventId());
    }
}
