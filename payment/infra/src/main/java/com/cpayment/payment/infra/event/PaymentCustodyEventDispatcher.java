package com.cpayment.payment.infra.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.port.CustodyEventSink;
import com.cpayment.payment.domain.usecase.RecordDepositUseCase;
import com.cpayment.payment.domain.usecase.UpdatePayoutFromTransferUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes normalized {@link CustodyEvent}s to the appropriate domain use case.
 *
 * <p>Exhaustive sealed-type matching means the compiler flags any newly-added event
 * type as a missing branch — preventing silent drops as the protocol evolves.
 */
@Component
public class PaymentCustodyEventDispatcher implements CustodyEventSink {

    private static final Logger log = LoggerFactory.getLogger(PaymentCustodyEventDispatcher.class);

    private final RecordDepositUseCase recordDeposit;
    private final UpdatePayoutFromTransferUseCase updatePayout;
    private final MeterRegistry meters;

    public PaymentCustodyEventDispatcher(RecordDepositUseCase recordDeposit,
                                         UpdatePayoutFromTransferUseCase updatePayout,
                                         MeterRegistry meters) {
        this.recordDeposit = recordDeposit;
        this.updatePayout = updatePayout;
        this.meters = meters;
    }

    @Override
    public void accept(CustodyEventEnvelope envelope) {
        switch (envelope.event()) {
            case CustodyEvent.DepositDetected e   -> recordDeposit.onDetected(e);
            case CustodyEvent.DepositConfirmed e  -> recordDeposit.onConfirmed(e);
            case CustodyEvent.TransferBroadcast e -> updatePayout.onBroadcast(e);
            case CustodyEvent.TransferConfirmed e -> updatePayout.onConfirmed(e);
            case CustodyEvent.TransferFailed e    -> updatePayout.onFailed(e);
            case CustodyEvent.TransferReplaced e  -> updatePayout.onReplaced(e);
            case CustodyEvent.ApprovalPending e   -> noteUnhandled("ApprovalPending", envelope);
            case CustodyEvent.ApprovalGranted e   -> noteUnhandled("ApprovalGranted", envelope);
            case CustodyEvent.ApprovalRejected e  -> noteUnhandled("ApprovalRejected", envelope);
            case CustodyEvent.BalanceStale e      -> noteUnhandled("BalanceStale", envelope);
        }
    }

    private void noteUnhandled(String typeName, CustodyEventEnvelope envelope) {
        Counter.builder("cpayment.custody.event.unhandled")
            .tags(Tags.of("type", typeName))
            .register(meters).increment();
        log.debug("event.unhandled type={} provider={} providerEventId={}",
            typeName, envelope.providerName(), envelope.providerEventId());
    }
}
