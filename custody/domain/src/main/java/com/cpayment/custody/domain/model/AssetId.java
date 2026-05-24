package com.cpayment.custody.domain.model;

import java.util.Objects;

/**
 * Canonical asset identifier: "{network.canonical}:{symbol}".
 * Examples: "eth:mainnet:usdc", "tron:mainnet:trc20-usdt", "btc:mainnet:btc".
 * Provider-specific mappings (e.g. cus-server's ("ETHEREUM","USDC")) live in adapter mapping classes.
 */
public record AssetId(NetworkId network, String symbol) {

    public AssetId {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.isBlank()) throw new IllegalArgumentException("symbol must be non-blank");
    }

    public static AssetId parse(String canonical) {
        int lastColon = canonical.lastIndexOf(':');
        if (lastColon <= 0 || lastColon == canonical.length() - 1) {
            throw new IllegalArgumentException("invalid asset canonical: " + canonical);
        }
        return new AssetId(NetworkId.parse(canonical.substring(0, lastColon)),
                           canonical.substring(lastColon + 1));
    }

    public String canonical() {
        return network.canonical() + ":" + symbol;
    }
}
