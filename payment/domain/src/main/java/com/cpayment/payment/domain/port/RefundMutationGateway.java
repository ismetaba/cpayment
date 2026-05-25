package com.cpayment.payment.domain.port;

import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundEvent;

import java.util.List;

public interface RefundMutationGateway {

    void apply(Refund updated, List<RefundEvent> events);
}
