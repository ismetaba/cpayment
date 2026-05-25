package com.cpayment.payment.infra.gas;

import com.cpayment.custody.domain.model.NetworkId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Scheduled job that
 * <ol>
 *   <li>checks each configured funder's own native balance and logs / counts a
 *       runway warning if low — so an unfunded funder doesn't silently break the
 *       whole automation; and</li>
 *   <li>walks every {@code monitored-address} entry and asks
 *       {@link GasFunderService} to ensure that address has enough gas.</li>
 * </ol>
 *
 * <p>Runs on Spring's default scheduler thread, sequentially per tick. Each
 * per-address call is short (one HTTP balance read, occasionally one HTTP transfer);
 * if the monitored list grows beyond a few dozen, lift this to the parallel-executor
 * pattern used in {@link com.cpayment.payment.infra.webhook.WebhookDispatcher}.
 */
@Component
public class GasMonitor {

    private static final Logger log = LoggerFactory.getLogger(GasMonitor.class);

    private final GasFunderProperties props;
    private final GasFunderService service;

    public GasMonitor(GasFunderProperties props, GasFunderService service) {
        this.props = props;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${cpayment.gas.poll-interval-millis:60000}")
    public void scan() {
        Map<NetworkId, GasFunderProperties.Funder> byNetwork = props.indexByNetwork();

        // 1. Per-funder runway check.
        for (GasFunderProperties.Funder funder : props.effectiveFunders()) {
            try {
                service.checkFunderRunway(funder);
            } catch (RuntimeException ex) {
                log.error("gas.monitor.funder-check-unexpected network={} reason={}",
                    funder.network(), ex.getMessage(), ex);
            }
        }

        // 2. Per-target top-up.
        for (GasFunderProperties.Monitored target : props.effectiveMonitored()) {
            GasFunderProperties.Funder funder = byNetwork.get(target.networkId());
            if (funder == null) {
                // The validator should have rejected this at boot, but defensively:
                log.warn("gas.monitor.no-funder-configured network={} address={}",
                    target.network(), target.address());
                continue;
            }
            try {
                service.ensureGas(funder, target.address());
            } catch (RuntimeException ex) {
                log.error("gas.monitor.unexpected network={} address={} reason={}",
                    target.network(), target.address(), ex.getMessage(), ex);
            }
        }
    }
}
