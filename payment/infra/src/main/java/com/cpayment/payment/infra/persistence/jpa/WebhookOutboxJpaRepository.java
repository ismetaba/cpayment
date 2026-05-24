package com.cpayment.payment.infra.persistence.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookOutboxJpaRepository extends JpaRepository<WebhookOutboxEntity, UUID> {

    @Query("select e from WebhookOutboxEntity e " +
           "where e.status = com.cpayment.payment.infra.persistence.jpa.WebhookOutboxEntity.Status.PENDING " +
           "and e.nextAttemptAt <= :cutoff " +
           "order by e.nextAttemptAt asc")
    List<WebhookOutboxEntity> findDueForDelivery(Instant cutoff, Pageable limit);
}
