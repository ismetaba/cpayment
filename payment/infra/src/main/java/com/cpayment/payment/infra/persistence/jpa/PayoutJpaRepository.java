package com.cpayment.payment.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayoutJpaRepository extends JpaRepository<PayoutEntity, UUID> {

    Optional<PayoutEntity> findByCustodyTransferId(UUID custodyTransferId);
}
