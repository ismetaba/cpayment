package com.cpayment.custody.infra.cusserver.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.CustodyEventEnvelope;
import com.cpayment.custody.domain.port.CustodyEventSink;
import com.cpayment.custody.infra.cusserver.event.dto.CreateDepositTransactionsEventDTO;
import com.cpayment.custody.infra.cusserver.event.dto.DepositTransactionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RabbitMQ → normalized {@link CustodyEventEnvelope} bridge for cus-server.
 *
 * <h2>Boundary contract</h2>
 * <ul>
 *   <li>Provider-specific payloads (cus-server DTOs) are NEVER exposed beyond this class.</li>
 *   <li>One inbound message may produce multiple outbound envelopes (deposit batches).</li>
 *   <li>Mapping failure on a single item is logged and the item is dropped; the rest still
 *       publish. We deliberately do NOT nack the entire batch — losing the whole batch
 *       because of one malformed payload would be worse than dropping it.</li>
 * </ul>
 *
 * <h2>Future work</h2>
 * <p>Once cus-server publishes a stable contract, swap the local DTOs for the canonical
 * classes and harden against duplicates via {@code providerEventId} dedupe at the
 * publisher side.
 */
@Component
public class CusServerEventBridge {

    private static final String PROVIDER = "cus-server";
    private static final Logger log = LoggerFactory.getLogger(CusServerEventBridge.class);

    private final CustodyEventSink sink;
    private final DepositEventMapper depositMapper;
    private final Clock clock;

    public CusServerEventBridge(CustodyEventSink sink,
                                DepositEventMapper depositMapper,
                                Clock clock) {
        this.sink = sink;
        this.depositMapper = depositMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = "${cpayment.custody.cusserver.rabbit.create-deposit-queue}")
    public void onCreateDeposit(CreateDepositTransactionsEventDTO payload) {
        if (payload == null || payload.depositTransactions() == null) {
            log.warn("event.deposit.empty payload=null-or-empty");
            return;
        }
        List<DepositTransactionDTO> items = payload.depositTransactions();
        for (DepositTransactionDTO d : items) {
            try {
                CustodyEvent.DepositDetected event = depositMapper.toDepositDetected(d);
                emit(event, providerEventIdOf(d), d.detectedAt());
            } catch (RuntimeException ex) {
                log.error("event.deposit.mapping-failed dto={} reason={}", d, ex.getMessage(), ex);
            }
        }
    }

    @RabbitListener(queues = "${cpayment.custody.cusserver.rabbit.update-transaction-queue}")
    public void onUpdateTransaction(Object payload) {
        // Out of scope for the first slice — outbound transfer state changes will be
        // mapped to TransferBroadcast/Confirmed/Failed/Replaced in a later iteration.
        log.debug("event.update-transaction received (mapping not implemented yet)");
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

    private String providerEventIdOf(DepositTransactionDTO d) {
        return d.id() != null ? d.id().toString() : "unknown";
    }
}
