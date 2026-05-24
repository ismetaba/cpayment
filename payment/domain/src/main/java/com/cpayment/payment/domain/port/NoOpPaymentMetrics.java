package com.cpayment.payment.domain.port;

import com.cpayment.custody.domain.model.AssetId;

/**
 * Default implementation that silently swallows every call. Hand to unit tests so
 * they don't need to mock metrics; production wiring replaces it with the
 * Micrometer-backed adapter.
 */
public final class NoOpPaymentMetrics implements PaymentMetrics {

    public static final NoOpPaymentMetrics INSTANCE = new NoOpPaymentMetrics();

    private NoOpPaymentMetrics() {}

    @Override public void invoiceCreated(AssetId asset) { /* no-op */ }
    @Override public void invoiceIdempotentHit() { /* no-op */ }
    @Override public void depositPaid(AssetId asset) { /* no-op */ }
    @Override public void depositUnderpaid(AssetId asset) { /* no-op */ }
    @Override public void depositOrphan() { /* no-op */ }
    @Override public void depositDuplicate() { /* no-op */ }
}
