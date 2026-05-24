package com.cpayment.custody.infra.cusserver.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.port.CustodyEventSink;
import com.cpayment.custody.infra.cusserver.event.dto.CreateDepositTransactionsEventDTO;
import com.cpayment.custody.infra.cusserver.event.dto.DepositTransactionDTO;
import com.cpayment.custody.infra.cusserver.observability.EventBridgeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RabbitMQ → normalized {@link CustodyEventEnvelope} bridge for cus-server.
 *
 * <h2>Observability</h2>
 * <ul>
 *   <li>Per-event correlation id placed in MDC for the duration of dispatch, so
 *       downstream use case logs are tied back to the upstream provider event.</li>
 *   <li>Counters for received / emitted / mapping-failed events, tagged by queue.</li>
 * </ul>
 *
 * <h2>Failure handling</h2>
 * <p>One inbound message may produce multiple outbound envelopes. Mapping failure on a
 * single item is logged and the item is dropped; the rest still publish. We deliberately
 * do NOT nack the entire batch — losing the whole batch because of one malformed item
 * is strictly worse than dropping it.
 */
@Component
public class CusServerEventBridge {

    private static final String PROVIDER = "cus-server";
    private static final String CREATE_DEPOSIT_QUEUE = "create-deposit";
    private static final String UPDATE_TRANSACTION_QUEUE = "update-transaction";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CusServerEventBridge.class);

    private final CustodyEventSink sink;
    private final DepositEventMapper depositMapper;
    private final EventBridgeMetrics metrics;
    private final Clock clock;

    public CusServerEventBridge(CustodyEventSink sink,
                                DepositEventMapper depositMapper,
                                EventBridgeMetrics metrics,
                                Clock clock) {
        this.sink = sink;
        this.depositMapper = depositMapper;
        this.metrics = metrics;
        this.clock = clock;
    }

    @RabbitListener(queues = "${cpayment.custody.cusserver.rabbit.create-deposit-queue}")
    public void onCreateDeposit(CreateDepositTransactionsEventDTO payload) {
        metrics.received(CREATE_DEPOSIT_QUEUE);
        if (payload == null || payload.depositTransactions() == null) {
            log.warn("event.deposit.empty queue={}", CREATE_DEPOSIT_QUEUE);
            return;
        }
        List<DepositTransactionDTO> items = payload.depositTransactions();
        for (DepositTransactionDTO d : items) {
            withCorrelationId(providerEventIdOf(d), () -> dispatchDeposit(d));
        }
    }

    @RabbitListener(queues = "${cpayment.custody.cusserver.rabbit.update-transaction-queue}")
    public void onUpdateTransaction(Object payload) {
        metrics.received(UPDATE_TRANSACTION_QUEUE);
        // Out of scope for the first slice — outbound transfer state changes will be
        // mapped to TransferBroadcast/Confirmed/Failed/Replaced in a later iteration.
        log.debug("event.update-transaction received (mapping not implemented yet)");
    }

    private void dispatchDeposit(DepositTransactionDTO d) {
        try {
            CustodyEvent.DepositDetected event = depositMapper.toDepositDetected(d);
            emit(event, providerEventIdOf(d), d.detectedAt());
            metrics.emitted(CREATE_DEPOSIT_QUEUE, "DepositDetected");
        } catch (RuntimeException ex) {
            metrics.mappingFailed(CREATE_DEPOSIT_QUEUE);
            log.error("event.deposit.mapping-failed dto={} reason={}", d, ex.getMessage(), ex);
        }
    }

    private void emit(CustodyEvent event, String providerEventId, Instant occurredAt) {
        Instant now = clock.instant();
        sink.accept(new CustodyEventEnvelope(
            UUID.randomUUID(),
            occurredAt != null ? occurredAt : now,
            now,
            PROVIDER,
            providerEventId,
            event
        ));
    }

    private static void withCorrelationId(String id, Runnable action) {
        String previous = MDC.get(MDC_CORRELATION_ID);
        MDC.put(MDC_CORRELATION_ID, id);
        try {
            action.run();
        } finally {
            if (previous != null) MDC.put(MDC_CORRELATION_ID, previous);
            else MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private static String providerEventIdOf(DepositTransactionDTO d) {
        return d.id() != null ? d.id().toString() : "unknown";
    }
}
