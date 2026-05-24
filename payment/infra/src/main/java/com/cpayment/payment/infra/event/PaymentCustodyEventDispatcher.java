package com.cpayment.payment.infra.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.port.CustodyEventSink;
import com.cpayment.payment.domain.usecase.RecordDepositUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * cpayment-side implementation of {@link CustodyEventSink}. Routes each normalized
 * envelope to the appropriate domain use case; unhandled variants are counted but
 * never throw.
 *
 * <p>Exhaustive pattern-matching on the sealed {@link CustodyEvent} hierarchy means
 * the compiler flags any newly-added event type as a missing branch — preventing
 * silent drops as the protocol evolves.
 */
@Component
public class PaymentCustodyEventDispatcher implements CustodyEventSink {

    private static final Logger log = LoggerFactory.getLogger(PaymentCustodyEventDispatcher.class);

    private final RecordDepositUseCase recordDeposit;
    private final MeterRegistry meters;

    public PaymentCustodyEventDispatcher(RecordDepositUseCase recordDeposit, MeterRegistry meters) {
        this.recordDeposit = recordDeposit;
        this.meters = meters;
    }

    @Override
    public void accept(CustodyEventEnvelope envelope) {
        switch (envelope.event()) {
            case CustodyEvent.DepositDetected e   -> recordDeposit.onDetected(e);
            case CustodyEvent.DepositConfirmed e  -> recordDeposit.onConfirmed(e);
            case CustodyEvent.TransferBroadcast e -> noteUnhandled("TransferBroadcast", envelope);
            case CustodyEvent.TransferConfirmed e -> noteUnhandled("TransferConfirmed", envelope);
            case CustodyEvent.TransferFailed e    -> noteUnhandled("TransferFailed", envelope);
            case CustodyEvent.TransferReplaced e  -> noteUnhandled("TransferReplaced", envelope);
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
