package com.cpayment.payment.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustodyIdempotencyClaimJpaRepository
    extends JpaRepository<CustodyIdempotencyClaimEntity, String> {
}
