package com.cpayment.custody.infra.cusserver.mapping;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetIdMapperTest {

    private final AssetIdMapper mapper = new AssetIdMapper();

    @Test
    void maps_usdc_round_trip() {
        AssetId usdc = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
        AssetIdMapper.CusServerAsset cus = mapper.toCusServer(usdc);
        assertThat(cus.networkName()).isEqualTo("ETHEREUM");
        assertThat(cus.assetName()).isEqualTo("USDC");
        assertThat(mapper.fromCusServer("ETHEREUM", "USDC")).isEqualTo(usdc);
    }

    @Test
    void maps_tron_usdt_round_trip() {
        AssetId trc20 = new AssetId(new NetworkId("tron", "mainnet"), "trc20-usdt");
        AssetIdMapper.CusServerAsset cus = mapper.toCusServer(trc20);
        assertThat(cus.networkName()).isEqualTo("TRON");
        assertThat(cus.assetName()).isEqualTo("USDT");
        assertThat(mapper.fromCusServer("TRON", "USDT")).isEqualTo(trc20);
    }

    @Test
    void rejects_unmapped_asset_to_cus() {
        AssetId unknown = new AssetId(new NetworkId("eth", "mainnet"), "xyz");
        assertThatThrownBy(() -> mapper.toCusServer(unknown))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unmapped");
    }

    @Test
    void rejects_unmapped_cus_pair() {
        assertThatThrownBy(() -> mapper.fromCusServer("UNKNOWN", "USDC"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
