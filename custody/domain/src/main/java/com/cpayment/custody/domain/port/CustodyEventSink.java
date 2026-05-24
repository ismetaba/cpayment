package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.event.CustodyEventEnvelope;

/**
 * Outbound port from the custody adapter into whoever consumes its events
 * (cpayment domain, an outbox writer, an audit log, etc.).
 *
 * <p>Named "Sink" intentionally: the adapter writes <em>into</em> this, the
 * consumer registers an implementation. The previous "Publisher" name suggested
 * the wrong direction — adapters publish, sinks receive.
 *
 * <p>Implementations must be:
 * <ul>
 *   <li>thread-safe (the bridge may invoke from multiple AMQP consumer threads);</li>
 *   <li>non-blocking enough not to back up the bridge — if heavy work is needed,
 *       enqueue and return.</li>
 * </ul>
 */
public interface CustodyEventSink {

    void accept(CustodyEventEnvelope envelope);
}
