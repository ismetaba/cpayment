package com.cpayment.custody.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetIdTest {

    @Test
    void parses_canonical_form() {
        AssetId id = AssetId.parse("eth:mainnet:usdc");
        assertThat(id.network()).isEqualTo(new NetworkId("eth", "mainnet"));
        assertThat(id.symbol()).isEqualTo("usdc");
        assertThat(id.canonical()).isEqualTo("eth:mainnet:usdc");
    }

    @Test
    void supports_compound_symbols_like_trc20_usdt() {
        AssetId id = AssetId.parse("tron:mainnet:trc20-usdt");
        assertThat(id.symbol()).isEqualTo("trc20-usdt");
    }

    @Test
    void rejects_missing_symbol() {
        assertThatThrownBy(() -> AssetId.parse("eth:mainnet:"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_malformed_canonical() {
        assertThatThrownBy(() -> AssetId.parse("eth-mainnet-usdc"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
