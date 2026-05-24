package com.cpayment.payment.domain.port;

import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;

import java.util.List;

/**
 * Atomic write boundary for payout state changes — saves the payout AND any domain
 * events in a single transaction. Mirrors {@link InvoiceMutationGateway}.
 */
public interface PayoutMutationGateway {

    void apply(Payout updated, List<PayoutEvent> events);
}
