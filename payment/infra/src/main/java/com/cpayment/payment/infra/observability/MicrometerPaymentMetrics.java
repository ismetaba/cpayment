package com.cpayment.payment.infra.observability;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.payment.domain.port.PaymentMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/**
 * Micrometer-backed counters. Tags are kept minimal to keep cardinality manageable —
 * asset (a fixed enumeration) is tagged; merchant id is intentionally omitted.
 *
 * <p>Counters are looked up per call (Micrometer caches internally) rather than cached
 * here, so adding a new asset to the system requires no code change.
 */
@Component
public class MicrometerPaymentMetrics implements PaymentMetrics {

    private final MeterRegistry registry;

    public MicrometerPaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void invoiceCreated(AssetId asset) {
        counter("cpayment.invoice.created", Tags.of("asset", asset.canonical())).increment();
    }

    @Override
    public void invoiceIdempotentHit() {
        counter("cpayment.invoice.idempotent_hit", Tags.empty()).increment();
    }

    @Override
    public void depositPaid(AssetId asset) {
        counter("cpayment.deposit.paid", Tags.of("asset", asset.canonical())).increment();
    }

    @Override
    public void depositUnderpaid(AssetId asset) {
        counter("cpayment.deposit.underpaid", Tags.of("asset", asset.canonical())).increment();
    }

    @Override
    public void depositOrphan() {
        counter("cpayment.deposit.orphan", Tags.empty()).increment();
    }

    @Override
    public void depositDuplicate() {
        counter("cpayment.deposit.duplicate", Tags.empty()).increment();
    }

    private Counter counter(String name, Iterable<Tag> tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
