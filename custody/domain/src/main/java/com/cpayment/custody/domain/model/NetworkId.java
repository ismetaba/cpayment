package com.cpayment.custody.domain.model;

import java.util.Objects;

/**
 * Canonical network identifier: "{chain}:{env}".
 * Examples: "eth:mainnet", "tron:mainnet", "btc:testnet".
 */
public record NetworkId(String chain, String env) {

    public NetworkId {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(env, "env");
        if (chain.isBlank() || env.isBlank()) {
            throw new IllegalArgumentException("chain and env must be non-blank");
        }
    }

    public static NetworkId parse(String canonical) {
        String[] parts = canonical.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid network canonical: " + canonical);
        }
        return new NetworkId(parts[0], parts[1]);
    }

    public String canonical() {
        return chain + ":" + env;
    }
}
