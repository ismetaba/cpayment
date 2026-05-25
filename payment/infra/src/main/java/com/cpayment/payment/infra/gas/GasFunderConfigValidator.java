package com.cpayment.payment.infra.gas;

import com.cpayment.custody.domain.model.NetworkId;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/**
 * Boot-time validator for {@link GasFunderProperties}. Runs as part of bean construction
 * so a misconfigured deployment fails fast at startup with a clear message — instead of
 * a baffling "balance check returns false but no top-up fires" at the first poll.
 *
 * <p>Specifically prevents:
 * <ul>
 *   <li>blank funder addresses,</li>
 *   <li>non-positive low-water-mark or top-up-amount,</li>
 *   <li>top-up-amount &lt;= low-water-mark (would trigger a top-up every tick because
 *       the new balance still sits at-or-below the threshold),</li>
 *   <li>native-asset whose network doesn't match the funder's network,</li>
 *   <li>duplicate funder entries for the same network,</li>
 *   <li>monitored addresses on networks that have no funder configured.</li>
 * </ul>
 */
@Component
public class GasFunderConfigValidator {

    public GasFunderConfigValidator(GasFunderProperties props) {
        validate(props);
    }

    private static void validate(GasFunderProperties props) {
        Set<NetworkId> seen = new HashSet<>();
        Set<NetworkId> fundedNetworks = new HashSet<>();

        for (GasFunderProperties.Funder f : props.effectiveFunders()) {
            NetworkId network = f.networkId();
            if (!seen.add(network)) {
                throw new IllegalStateException(
                    "duplicate gas funder entry for network " + network.canonical());
            }
            fundedNetworks.add(network);

            if (f.fromAddress() == null || f.fromAddress().isBlank()) {
                throw new IllegalStateException(
                    "gas funder " + network.canonical() + ": fromAddress is blank");
            }
            if (notPositive(f.lowWaterMark())) {
                throw new IllegalStateException(
                    "gas funder " + network.canonical() + ": lowWaterMark must be positive");
            }
            if (notPositive(f.topUpAmount())) {
                throw new IllegalStateException(
                    "gas funder " + network.canonical() + ": topUpAmount must be positive");
            }
            if (f.topUpAmount().compareTo(f.lowWaterMark()) <= 0) {
                throw new IllegalStateException(
                    "gas funder " + network.canonical()
                        + ": topUpAmount (" + f.topUpAmount() + ") must be > lowWaterMark ("
                        + f.lowWaterMark() + ") — otherwise every tick re-fires a top-up");
            }
            if (!network.equals(f.nativeAssetId().network())) {
                throw new IllegalStateException(
                    "gas funder " + network.canonical()
                        + ": nativeAsset " + f.nativeAsset() + " is on a different network");
            }
        }

        for (GasFunderProperties.Monitored m : props.effectiveMonitored()) {
            NetworkId net = m.networkId();
            if (!fundedNetworks.contains(net)) {
                throw new IllegalStateException(
                    "monitored gas address " + m.address() + " is on network "
                        + net.canonical() + " but no funder is configured for that network");
            }
        }
    }

    private static boolean notPositive(BigInteger v) { return v == null || v.signum() <= 0; }
}
