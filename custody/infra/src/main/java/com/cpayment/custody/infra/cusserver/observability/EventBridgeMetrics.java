package com.cpayment.custody.infra.cusserver.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/**
 * Counters for the cus-server RabbitMQ bridge. Tagged by queue and outcome to keep
 * cardinality bounded.
 */
@Component
public class EventBridgeMetrics {

    private final MeterRegistry registry;

    public EventBridgeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void received(String queue) {
        counter("cpayment.custody.event.received", queue, null).increment();
    }

    public void emitted(String queue, String eventType) {
        counter("cpayment.custody.event.emitted", queue, eventType).increment();
    }

    public void mappingFailed(String queue) {
        counter("cpayment.custody.event.mapping_failed", queue, null).increment();
    }

    public void unhandled(String typeName) {
        Counter.builder("cpayment.custody.event.unhandled")
            .tags(Tags.of("type", typeName))
            .register(registry).increment();
    }

    private Counter counter(String name, String queue, String type) {
        Tags tags = Tags.of("queue", queue);
        if (type != null) tags = tags.and("type", type);
        return Counter.builder(name).tags(tags).register(registry);
    }
}
