package com.cpayment.custody.infra.cusserver.mapping;

import com.cpayment.custody.domain.model.NetworkId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates between cpayment canonical {@link NetworkId} ("eth:mainnet") and the
 * uppercased single-token form cus-server uses ("ETHEREUM").
 *
 * <p>Kept separate from {@code AssetIdMapper} (SRP): network mapping is a concern that
 * exists independently of asset mapping (e.g. listing supported networks, address
 * validation per-network) and should not require an asset context.
 */
@Component
public class NetworkIdMapper {

    private final Map<NetworkId, String> toCus = new HashMap<>();
    private final Map<String, NetworkId> fromCus = new HashMap<>();

    public NetworkIdMapper() {
        register(new NetworkId("eth", "mainnet"),  "ETHEREUM");
        register(new NetworkId("tron", "mainnet"), "TRON");
        register(new NetworkId("btc", "mainnet"),  "BITCOIN");
        register(new NetworkId("sol", "mainnet"),  "SOLANA");
        register(new NetworkId("xrp", "mainnet"),  "XRP");
    }

    private void register(NetworkId canonical, String cusName) {
        toCus.put(canonical, cusName);
        fromCus.put(cusName, canonical);
    }

    public String toCusServer(NetworkId id) {
        String name = toCus.get(id);
        if (name == null) throw new IllegalArgumentException("unmapped network: " + id.canonical());
        return name;
    }

    public NetworkId fromCusServer(String cusName) {
        NetworkId id = fromCus.get(cusName);
        if (id == null) throw new IllegalArgumentException("unmapped cus-server network: " + cusName);
        return id;
    }
}
