package com.cpayment.payment.infra.gas;

import com.cpayment.custody.domain.model.NetworkId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Scheduled job that runs through every {@code monitored-address} entry, looks up
 * the matching {@code funder} for its network, and asks {@link GasFunderService}
 * to ensure the address has enough native gas.
 *
 * <p>The job is bound to a single thread by Spring's default scheduler — fine,
 * because each per-address call is short (one HTTP balance read, occasionally one
 * HTTP transfer). If the address list ever grows beyond a few dozen, switch to
 * the webhook-style executor pattern used in {@code WebhookDispatcher}.
 *
 * <p>A misconfiguration (monitored address on a network with no configured funder)
 * is logged on each tick — operators see the warning and either add the funder or
 * remove the monitor.
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
        for (GasFunderProperties.Monitored target : props.effectiveMonitored()) {
            GasFunderProperties.Funder funder = byNetwork.get(target.networkId());
            if (funder == null) {
                log.warn("gas.monitor.no-funder-configured network={} address={}",
                    target.network(), target.address());
                continue;
            }
            try {
                service.ensureGas(funder, target.address());
            } catch (RuntimeException ex) {
                // ensureGas already absorbs typed failures; this catch shields the
                // scheduler from any unexpected exception class.
                log.error("gas.monitor.unexpected network={} address={} reason={}",
                    target.network(), target.address(), ex.getMessage(), ex);
            }
        }
    }
}
