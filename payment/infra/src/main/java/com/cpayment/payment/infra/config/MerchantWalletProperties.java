package com.cpayment.payment.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

/**
 * Bootstrap configuration: pins which custody wallet hosts each (merchant, network) tuple.
 * For first-slice setups; production should join the custody wallet from a DB table.
 *
 * <pre>
 * cpayment.payment.merchant-wallets:
 *   - merchant-id: 11111111-1111-1111-1111-111111111111
 *     network: eth:mainnet
 *     wallet-id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
 * </pre>
 */
@ConfigurationProperties(prefix = "cpayment.payment")
public record MerchantWalletProperties(List<Entry> merchantWallets) {

    public record Entry(UUID merchantId, String network, UUID walletId) {}
}
