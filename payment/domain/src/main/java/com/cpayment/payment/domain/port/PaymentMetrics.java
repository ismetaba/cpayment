package com.cpayment.payment.domain.port;

import com.cpayment.custody.domain.model.AssetId;

/**
 * Outbound port for emitting payment-domain counters. Defined here (not in infra)
 * so use cases can declare a stable, framework-free dependency.
 *
 * <p>Implementations must be cheap to call on the hot path and must NOT throw under
 * any circumstance — metrics emission MUST NOT corrupt business flow. A no-op
 * implementation ({@link NoOpPaymentMetrics}) is provided for unit tests.
 */
public interface PaymentMetrics {

    void invoiceCreated(AssetId asset);
    void invoiceIdempotentHit();
    void depositPaid(AssetId asset);
    void depositUnderpaid(AssetId asset);
    void depositOrphan();
    void depositDuplicate();
}
