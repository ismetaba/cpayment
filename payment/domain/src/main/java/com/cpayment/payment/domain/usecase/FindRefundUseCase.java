package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.exception.RefundNotFoundException;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundId;
import com.cpayment.payment.domain.port.RefundRepository;

import java.util.Objects;

public final class FindRefundUseCase {

    private final RefundRepository refunds;

    public FindRefundUseCase(RefundRepository refunds) {
        this.refunds = Objects.requireNonNull(refunds, "refunds");
    }

    public Refund byId(RefundId id) {
        Objects.requireNonNull(id, "id");
        return refunds.findById(id).orElseThrow(() -> new RefundNotFoundException(id));
    }
}
