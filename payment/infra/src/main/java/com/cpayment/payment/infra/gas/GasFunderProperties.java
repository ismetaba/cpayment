package com.cpayment.payment.infra.gas;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigInteger;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for the gas-funder automation:
 *
 * <pre>
 * cpayment.gas:
 *   poll-interval: PT60S
 *   funders:
 *     - network:        eth:mainnet
 *       native-asset:   eth:mainnet:eth
 *       from-address:   0xGASFUNDER
 *       low-water-mark: 100000000000000000    # 0.1 ETH (wei)
 *       top-up-amount:  500000000000000000    # 0.5 ETH
 *   monitored-addresses:
 *     - network: eth:mainnet
 *       address: 0xWORKER
 * </pre>
 *
 * <p>Two-section layout: {@code funders} defines how to top up each network;
 * {@code monitored-addresses} declares which addresses the scheduler should keep
 * topped up. Production typically wires monitored addresses from a DB query rather
 * than static config; for first cut, config is fine.
 */
@ConfigurationProperties(prefix = "cpayment.gas")
public record GasFunderProperties(
    Duration pollInterval,
    List<Funder> funders,
    List<Monitored> monitoredAddresses
) {

    public Duration effectivePollInterval() {
        return pollInterval != null ? pollInterval : Duration.ofSeconds(60);
    }

    public List<Funder> effectiveFunders()        { return funders != null ? funders : List.of(); }
    public List<Monitored> effectiveMonitored()   { return monitoredAddresses != null ? monitoredAddresses : List.of(); }

    /** Indexed view for O(1) network → funder lookup. Built once at boot. */
    public Map<NetworkId, Funder> indexByNetwork() {
        Map<NetworkId, Funder> map = new HashMap<>();
        for (Funder f : effectiveFunders()) {
            map.put(NetworkId.parse(f.network()), f);
        }
        return Map.copyOf(map);
    }

    public record Funder(
        String network,         // "eth:mainnet"
        String nativeAsset,     // "eth:mainnet:eth"
        String fromAddress,
        BigInteger lowWaterMark,
        BigInteger topUpAmount,
        String memo             // optional
    ) {
        public NetworkId networkId() { return NetworkId.parse(network); }
        public AssetId nativeAssetId() { return AssetId.parse(nativeAsset); }
        public Optional<String> memoOpt() {
            return memo == null || memo.isBlank() ? Optional.empty() : Optional.of(memo);
        }
    }

    public record Monitored(String network, String address) {
        public NetworkId networkId() { return NetworkId.parse(network); }
    }
}
