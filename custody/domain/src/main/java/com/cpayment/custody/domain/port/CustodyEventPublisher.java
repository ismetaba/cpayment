package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.event.CustodyEventEnvelope;

/**
 * Outbound port from the adapter/bridge into cpayment domain.
 * The adapter calls this with normalized envelopes; cpayment provides the implementation
 * (e.g. an internal event bus, an outbox table writer, etc.).
 */
public interface CustodyEventPublisher {
    void publish(CustodyEventEnvelope envelope);
}
