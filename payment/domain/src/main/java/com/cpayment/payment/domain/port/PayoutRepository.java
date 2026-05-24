package com.cpayment.payment.domain.port;

import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutId;

import java.util.Optional;

public interface PayoutRepository {

    Optional<Payout> findById(PayoutId id);

    /** Used by the Transfer*-event handlers to advance status on the right payout. */
    Optional<Payout> findByCustodyTransferId(TransferId transferId);
}
