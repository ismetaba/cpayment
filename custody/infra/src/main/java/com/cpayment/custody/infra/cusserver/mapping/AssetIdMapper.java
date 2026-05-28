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
    /** cus-server network name → that network's native gas asset. */
    private final Map<String, AssetId> nativeByCusNetwork = new HashMap<>();

    public AssetIdMapper() {
        // seed with common assets; production should load from config/registry
        register(new AssetId(new NetworkId("eth", "mainnet"), "eth"),    "ETHEREUM",       "ETH");
        register(new AssetId(new NetworkId("eth", "mainnet"), "usdc"),   "ETHEREUM",       "USDC");
        register(new AssetId(new NetworkId("eth", "mainnet"), "usdt"),   "ETHEREUM",       "USDT");
        register(new AssetId(new NetworkId("tron", "mainnet"), "trx"),   "TRON",           "TRX");
        register(new AssetId(new NetworkId("tron", "mainnet"), "trc20-usdt"), "TRON",      "USDT");
        register(new AssetId(new NetworkId("btc", "mainnet"), "btc"),    "BITCOIN",        "BTC");

        // Native gas asset per network — a transaction fee is always paid in the
        // network's native asset, so this lets us attribute a fee when the provider
        // reports the fee network but not the fee asset.
        markNative("ETHEREUM", "ETH");
        markNative("TRON",     "TRX");
        markNative("BITCOIN",  "BTC");
    }

    private void markNative(String cusNetworkName, String nativeAssetName) {
        nativeByCusNetwork.put(cusNetworkName, fromCusServer(cusNetworkName, nativeAssetName));
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

    /** The native gas asset of a cus-server network (e.g. "ETHEREUM" → eth:mainnet:eth). */
    public AssetId nativeAssetOf(String cusNetworkName) {
        AssetId id = nativeByCusNetwork.get(cusNetworkName);
        if (id == null) throw new IllegalArgumentException(
            "no native asset registered for cus-server network: " + cusNetworkName);
        return id;
    }
}
