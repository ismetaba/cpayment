package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.model.TransferId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared scaffolding for advancing a typestate aggregate (Payout / Refund) on inbound
 * Transfer* custody events. The lookup-by-transfer-id, the "matched but dropped" vs
 * "no match" return contract, the redelivery / after-terminal drop handling, and the
 * structured logging are identical across resources; only the per-variant transition
 * switch and the resource-specific event/gateway persistence differ, and those are
 * supplied by the subclass at each call site.
 *
 * @param <D> the aggregate type being advanced
 */
abstract class AbstractTransferEventApplier<D> {

    private static final Logger log = LoggerFactory.getLogger(AbstractTransferEventApplier.class);

    protected final Clock clock;
    private final String resource;

    protected AbstractTransferEventApplier(Clock clock, String resource) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resource = resource;
    }

    /** Locate the aggregate that owns the given custody transfer, if any. */
    protected abstract Optional<D> findByTransferId(TransferId transferId);

    /** Stable id of the aggregate, for log lines. */
    protected abstract Object idOf(D aggregate);

    /** Status projection of the aggregate, for log lines. */
    protected abstract Object statusOf(D aggregate);

    /**
     * Apply a transition and persist the result.
     *
     * @param transition maps the current aggregate to its next state, or returns
     *                   {@code null} to drop the event (redelivery / after-terminal)
     *                   without persisting
     * @param persist    persists the advanced aggregate and emits its domain event
     * @return {@code true} if the transfer matched an aggregate (handled — whether updated
     *         or deliberately dropped); {@code false} if no aggregate matched
     */
    protected final boolean advance(TransferId transferId, String op,
                                    Function<D, D> transition, Consumer<D> persist) {
        Optional<D> maybe = findByTransferId(transferId);
        if (maybe.isEmpty()) return false;
        D before = maybe.get();
        D next = transition.apply(before);
        if (next == null) return true; // matched, but nothing to save
        persist.accept(next);
        log.info("{}.advanced id={} {} -> {} transferId={}",
            resource, idOf(before), statusOf(before), statusOf(next), transferId.value());
        return true;
    }

    protected final D dropRedelivery(String op, D aggregate) {
        log.warn("{}.{}-redelivery id={} status={}", resource, op, idOf(aggregate), statusOf(aggregate));
        return null;
    }

    protected final D dropTerminal(String op, D aggregate) {
        log.warn("{}.{}-after-terminal id={} status={}", resource, op, idOf(aggregate), statusOf(aggregate));
        return null;
    }
}
