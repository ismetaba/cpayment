package com.cpayment.payment.infra.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.CustodyEventSink;
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
 * Routes normalized {@link CustodyEvent}s to the appropriate domain use case.
 *
 * <h2>Resource resolution for Transfer* events</h2>
 * <p>cus-server only knows about {@link TransferId}s — it has no notion of "payout"
 * vs "refund". Both use cases return {@code boolean} indicating whether they found
 * a matching aggregate; the dispatcher chains them with the same payout-first
 * preference (most volume comes from payouts) and records the event as orphan
 * only when both miss.
 *
 * <p>This symmetric routing replaces the earlier asymmetric pattern where the
 * dispatcher did an explicit {@code findByCustodyTransferId} lookup for payouts
 * before calling the use case — that produced two DB queries (dispatcher + use
 * case lookup) per payout event. The chain now does one lookup per use case,
 * short-circuits on the first hit, and stays symmetric.
 */
@Component
public class PaymentCustodyEventDispatcher implements CustodyEventSink {

    private static final Logger log = LoggerFactory.getLogger(PaymentCustodyEventDispatcher.class);

    private final RecordDepositUseCase recordDeposit;
    private final UpdatePayoutFromTransferUseCase updatePayout;
    private final UpdateRefundFromTransferUseCase updateRefund;
    private final MeterRegistry meters;

    public PaymentCustodyEventDispatcher(RecordDepositUseCase recordDeposit,
                                         UpdatePayoutFromTransferUseCase updatePayout,
                                         UpdateRefundFromTransferUseCase updateRefund,
                                         MeterRegistry meters) {
        this.recordDeposit = recordDeposit;
        this.updatePayout = updatePayout;
        this.updateRefund = updateRefund;
        this.meters = meters;
    }

    @Override
    public void accept(CustodyEventEnvelope envelope) {
        switch (envelope.event()) {
            case CustodyEvent.DepositDetected e   -> recordDeposit.onDetected(e);
            case CustodyEvent.DepositConfirmed e  -> recordDeposit.onConfirmed(e);
            case CustodyEvent.TransferBroadcast e -> routeTransfer("TransferBroadcast", e.id(), envelope,
                                                          () -> updatePayout.onBroadcast(e),
                                                          () -> updateRefund.onBroadcast(e));
            case CustodyEvent.TransferConfirmed e -> routeTransfer("TransferConfirmed", e.id(), envelope,
                                                          () -> updatePayout.onConfirmed(e),
                                                          () -> updateRefund.onConfirmed(e));
            case CustodyEvent.TransferFailed e    -> routeTransfer("TransferFailed", e.id(), envelope,
                                                          () -> updatePayout.onFailed(e),
                                                          () -> updateRefund.onFailed(e));
            case CustodyEvent.TransferReplaced e  -> routeTransfer("TransferReplaced", e.oldId(), envelope,
                                                          () -> updatePayout.onReplaced(e),
                                                          () -> false); // refunds don't support RBF yet
            case CustodyEvent.ApprovalPending e   -> noteUnhandled("ApprovalPending", envelope);
            case CustodyEvent.ApprovalGranted e   -> noteUnhandled("ApprovalGranted", envelope);
            case CustodyEvent.ApprovalRejected e  -> noteUnhandled("ApprovalRejected", envelope);
            case CustodyEvent.BalanceStale e      -> noteUnhandled("BalanceStale", envelope);
        }
    }

    private void routeTransfer(String type, TransferId transferId, CustodyEventEnvelope env,
                               java.util.function.BooleanSupplier payoutHandler,
                               java.util.function.BooleanSupplier refundHandler) {
        if (payoutHandler.getAsBoolean()) return;
        if (refundHandler.getAsBoolean()) return;
        Counter.builder("cpayment.custody.event.orphan")
            .tags(Tags.of("type", type)).register(meters).increment();
        log.warn("event.orphan type={} transferId={} providerEventId={}",
            type, transferId.value(), env.providerEventId());
    }

    private void noteUnhandled(String typeName, CustodyEventEnvelope envelope) {
        Counter.builder("cpayment.custody.event.unhandled")
            .tags(Tags.of("type", typeName)).register(meters).increment();
        log.debug("event.unhandled type={} provider={} providerEventId={}",
            typeName, envelope.providerName(), envelope.providerEventId());
    }
}
