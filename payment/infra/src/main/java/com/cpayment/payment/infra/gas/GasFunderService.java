package com.cpayment.payment.infra.gas;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.Balance;
import com.cpayment.custody.domain.model.FeePreference;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.BalancePort;
import com.cpayment.custody.domain.port.TransferPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Optional;

/**
 * Pure logic for "ensure this address has at least the low-water-mark of native gas
 * on its chain; if not, fund it from the configured gas funder wallet."
 *
 * <h2>Counters (network-tagged)</h2>
 * <ul>
 *   <li>{@code cpayment.gas.topup.fired}    — sent a top-up tx</li>
 *   <li>{@code cpayment.gas.topup.skipped}  — balance was already sufficient</li>
 *   <li>{@code cpayment.gas.topup.failed}   — balance read or transfer threw</li>
 *   <li>{@code cpayment.gas.funder.low}    — funder wallet itself is below runway threshold</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <p>Each top-up uses an adapter-level idempotency key bucketed by the wall-clock
 * hour ({@code "gas-{address}-{epochHour}"}), so two scheduler ticks within the
 * same hour cannot fire duplicate top-ups even if both see a low balance.
 *
 * <h2>Failure handling</h2>
 * <p>Balance-read and transfer failures are absorbed and logged; the method returns
 * {@code false} and the next tick retries. No exception propagates to the scheduler
 * so one bad address can't break the iteration.
 */
@Component
public class GasFunderService {

    private static final Logger log = LoggerFactory.getLogger(GasFunderService.class);
    private static final long HOUR_SECONDS = 3600L;
    /** Funder is "low" when its native balance < topUpAmount × this multiplier. */
    private static final int RUNWAY_MULTIPLIER = 5;

    private final BalancePort balancePort;
    private final TransferPort transferPort;
    private final Clock clock;
    private final MeterRegistry meters;

    public GasFunderService(BalancePort balancePort,
                            TransferPort transferPort,
                            Clock clock,
                            MeterRegistry meters) {
        this.balancePort = balancePort;
        this.transferPort = transferPort;
        this.clock = clock;
        this.meters = meters;
    }

    /**
     * Ensure the given address has at least {@code funder.lowWaterMark()} units of
     * native gas. Returns {@code true} if a top-up was actually fired in this call.
     */
    public boolean ensureGas(GasFunderProperties.Funder funder, String targetAddress) {
        NetworkId network = funder.networkId();
        AssetId nativeAsset = funder.nativeAssetId();
        if (!nativeAsset.network().equals(network)) {
            log.warn("gas.config-mismatch network={} nativeAsset={} — skipping",
                network.canonical(), nativeAsset.canonical());
            return false;
        }

        BigInteger available = readBalance(network, nativeAsset, targetAddress);
        if (available == null) {
            counter("cpayment.gas.topup.failed", network).increment();
            return false;
        }

        if (available.compareTo(funder.lowWaterMark()) >= 0) {
            counter("cpayment.gas.topup.skipped", network).increment();
            log.debug("gas.sufficient network={} address={} balance={}",
                network.canonical(), targetAddress, available);
            return false;
        }

        try {
            TransferId tx = transferPort.sendTransfer(new SendTransferCommand(
                bucketedKey(targetAddress),
                funder.fromAddress(),
                targetAddress,
                nativeAsset,
                funder.topUpAmount(),
                funder.memoOpt(),
                FeePreference.NORMAL
            ));
            counter("cpayment.gas.topup.fired", network).increment();
            log.info("gas.topup.fired network={} address={} amount={} below={} transferId={}",
                network.canonical(), targetAddress, funder.topUpAmount(), funder.lowWaterMark(),
                tx.value());
            return true;
        } catch (RuntimeException ex) {
            counter("cpayment.gas.topup.failed", network).increment();
            log.warn("gas.topup.failed network={} address={} reason={}",
                network.canonical(), targetAddress, ex.getMessage());
            return false;
        }
    }

    /**
     * Watch the funder wallet itself — if its native balance drops below
     * {@code topUpAmount × RUNWAY_MULTIPLIER}, increment {@code cpayment.gas.funder.low}
     * and log a warning. Operators wire that counter to an alert; an unfunded funder
     * silently kills the whole automation otherwise.
     */
    public void checkFunderRunway(GasFunderProperties.Funder funder) {
        NetworkId network = funder.networkId();
        AssetId nativeAsset = funder.nativeAssetId();
        BigInteger balance = readBalance(network, nativeAsset, funder.fromAddress());
        if (balance == null) return;

        BigInteger threshold = funder.topUpAmount().multiply(BigInteger.valueOf(RUNWAY_MULTIPLIER));
        if (balance.compareTo(threshold) < 0) {
            counter("cpayment.gas.funder.low", network).increment();
            log.warn("gas.funder.low network={} fromAddress={} balance={} threshold={} runwayTopUps={}",
                network.canonical(), funder.fromAddress(), balance, threshold, RUNWAY_MULTIPLIER);
        }
    }

    private BigInteger readBalance(NetworkId network, AssetId asset, String address) {
        try {
            Balance b = balancePort.getBalanceByAddress(network, asset, address);
            if (b == null || b.status() == Balance.BalanceStatus.UNAVAILABLE) {
                log.warn("gas.balance-unavailable network={} address={}",
                    network.canonical(), address);
                return null;
            }
            return Optional.ofNullable(b.available()).orElse(BigInteger.ZERO);
        } catch (RuntimeException ex) {
            log.warn("gas.balance-read-failed network={} address={} reason={}",
                network.canonical(), address, ex.getMessage());
            return null;
        }
    }

    private IdempotencyKey bucketedKey(String targetAddress) {
        long epochHour = clock.instant().getEpochSecond() / HOUR_SECONDS;
        return IdempotencyKey.of("gas-" + targetAddress + "-" + epochHour);
    }

    private Counter counter(String name, NetworkId network) {
        return Counter.builder(name).tags(Tags.of("network", network.canonical())).register(meters);
    }
}
