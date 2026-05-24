package com.cpayment.payment.domain.usecase;

import com.cpayment.payment.domain.exception.PayoutNotFoundException;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.port.PayoutRepository;

import java.util.Objects;

public final class FindPayoutUseCase {

    private final PayoutRepository payouts;

    public FindPayoutUseCase(PayoutRepository payouts) {
        this.payouts = Objects.requireNonNull(payouts, "payouts");
    }

    public Payout byId(PayoutId id) {
        Objects.requireNonNull(id, "id");
        return payouts.findById(id).orElseThrow(() -> new PayoutNotFoundException(id));
    }
}
