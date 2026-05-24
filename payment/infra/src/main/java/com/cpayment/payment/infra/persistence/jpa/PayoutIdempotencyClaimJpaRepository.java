package com.cpayment.payment.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutIdempotencyClaimJpaRepository
    extends JpaRepository<PayoutIdempotencyClaimEntity, String> {
}
