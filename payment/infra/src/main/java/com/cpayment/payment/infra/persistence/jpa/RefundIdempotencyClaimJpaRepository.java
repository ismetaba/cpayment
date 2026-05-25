package com.cpayment.payment.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundIdempotencyClaimJpaRepository
    extends JpaRepository<RefundIdempotencyClaimEntity, String> {
}
