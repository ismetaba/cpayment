package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.port.PayoutRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class JpaPayoutRepository implements PayoutRepository {

    private final PayoutJpaRepository jpa;

    public JpaPayoutRepository(PayoutJpaRepository jpa) { this.jpa = jpa; }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payout> findById(PayoutId id) {
        return jpa.findById(id.value()).map(PayoutMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payout> findByCustodyTransferId(TransferId transferId) {
        return jpa.findByCustodyTransferId(transferId.value()).map(PayoutMapper::toDomain);
    }
}
