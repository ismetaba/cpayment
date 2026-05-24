package com.cpayment.payment.infra.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.port.CustodyEventSink;
import com.cpayment.payment.domain.usecase.RecordDepositUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The cpayment-side implementation of {@link CustodyEventSink}. Receives normalized
 * {@link CustodyEventEnvelope}s from the custody bridge and routes each variant to
 * the appropriate domain use case.
 *
 * <p>Exhaustive pattern-matching on the sealed {@link CustodyEvent} hierarchy means
 * the compiler flags any newly-added event type as a missing branch — preventing
 * silent drops as the protocol evolves.
 */
@Component
public class PaymentCustodyEventDispatcher implements CustodyEventSink {

    private static final Logger log = LoggerFactory.getLogger(PaymentCustodyEventDispatcher.class);

    private final RecordDepositUseCase recordDeposit;

    public PaymentCustodyEventDispatcher(RecordDepositUseCase recordDeposit) {
        this.recordDeposit = recordDeposit;
    }

    @Override
    public void accept(CustodyEventEnvelope envelope) {
        switch (envelope.event()) {
            case CustodyEvent.DepositDetected e   -> recordDeposit.handle(e);
            case CustodyEvent.DepositConfirmed e  -> handleNotYet("DepositConfirmed", envelope);
            case CustodyEvent.TransferBroadcast e -> handleNotYet("TransferBroadcast", envelope);
            case CustodyEvent.TransferConfirmed e -> handleNotYet("TransferConfirmed", envelope);
            case CustodyEvent.TransferFailed e    -> handleNotYet("TransferFailed", envelope);
            case CustodyEvent.TransferReplaced e  -> handleNotYet("TransferReplaced", envelope);
            case CustodyEvent.ApprovalPending e   -> handleNotYet("ApprovalPending", envelope);
            case CustodyEvent.ApprovalGranted e   -> handleNotYet("ApprovalGranted", envelope);
            case CustodyEvent.ApprovalRejected e  -> handleNotYet("ApprovalRejected", envelope);
            case CustodyEvent.BalanceStale e      -> handleNotYet("BalanceStale", envelope);
        }
    }

    private void handleNotYet(String typeName, CustodyEventEnvelope envelope) {
        log.debug("event.unhandled type={} provider={} providerEventId={}",
                  typeName, envelope.providerName(), envelope.providerEventId());
    }
}
