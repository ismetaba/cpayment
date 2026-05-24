package com.cpayment.custody.infra.cusserver.mapping;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates between cpayment canonical AssetId ("eth:mainnet:usdc") and
 * cus-server's (networkName, assetName) pair ("ETHEREUM", "USDC").
 *
 * Bidirectional table is the source of truth. Adding a new asset = adding one row.
 */
@Component
public class AssetIdMapper {

    public record CusServerAsset(String networkName, String assetName) {}

    private final Map<AssetId, CusServerAsset> toCus = new HashMap<>();
    private final Map<CusServerAsset, AssetId> fromCus = new HashMap<>();

    public AssetIdMapper() {
        // seed with common assets; production should load from config/registry
        register(new AssetId(new NetworkId("eth", "mainnet"), "eth"),    "ETHEREUM",       "ETH");
        register(new AssetId(new NetworkId("eth", "mainnet"), "usdc"),   "ETHEREUM",       "USDC");
        register(new AssetId(new NetworkId("eth", "mainnet"), "usdt"),   "ETHEREUM",       "USDT");
        register(new AssetId(new NetworkId("tron", "mainnet"), "trx"),   "TRON",           "TRX");
        register(new AssetId(new NetworkId("tron", "mainnet"), "trc20-usdt"), "TRON",      "USDT");
        register(new AssetId(new NetworkId("btc", "mainnet"), "btc"),    "BITCOIN",        "BTC");
    }

    private void register(AssetId canonical, String network, String asset) {
        CusServerAsset cus = new CusServerAsset(network, asset);
        toCus.put(canonical, cus);
        fromCus.put(cus, canonical);
    }

    public CusServerAsset toCusServer(AssetId id) {
        CusServerAsset cus = toCus.get(id);
        if (cus == null) throw new IllegalArgumentException("unmapped asset: " + id.canonical());
        return cus;
    }

    public AssetId fromCusServer(String networkName, String assetName) {
        AssetId id = fromCus.get(new CusServerAsset(networkName, assetName));
        if (id == null) throw new IllegalArgumentException("unmapped cus-server asset: "
            + networkName + "/" + assetName);
        return id;
    }
}
