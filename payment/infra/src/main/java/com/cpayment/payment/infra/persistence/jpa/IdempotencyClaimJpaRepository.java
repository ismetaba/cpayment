package com.cpayment.payment.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyClaimJpaRepository extends JpaRepository<IdempotencyClaimEntity, String> {
}
