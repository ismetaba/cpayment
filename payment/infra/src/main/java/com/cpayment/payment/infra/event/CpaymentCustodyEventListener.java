package com.cpayment.payment.infra.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.port.CustodyEventPublisher;
import com.cpayment.payment.domain.usecase.RecordDepositUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter into cpayment: receives normalized {@link CustodyEventEnvelope}s
 * from the custody bridge and dispatches each variant to the appropriate use case.
 *
 * <p>Exhaustive pattern-matching on the sealed {@link CustodyEvent} hierarchy means the
 * compiler will flag any newly-added event type as a missing branch — preventing silent
 * drops as the protocol evolves.
 */
@Component
public class CpaymentCustodyEventListener implements CustodyEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CpaymentCustodyEventListener.class);

    private final RecordDepositUseCase recordDeposit;

    public CpaymentCustodyEventListener(RecordDepositUseCase recordDeposit) {
        this.recordDeposit = recordDeposit;
    }

    @Override
    public void publish(CustodyEventEnvelope envelope) {
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
