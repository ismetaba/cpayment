package com.cpayment.payment.infra.persistence;

import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.payment.domain.exception.MerchantWalletNotConfiguredException;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.port.MerchantWalletResolver;
import com.cpayment.payment.infra.config.MerchantWalletProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves merchant → wallet bindings from {@link MerchantWalletProperties}. Snapshot taken
 * at construction; runtime additions require a restart (acceptable for first-slice config).
 */
@Component
public class ConfigDrivenMerchantWalletResolver implements MerchantWalletResolver {

    private record Key(MerchantId merchant, NetworkId network) {}

    private final Map<Key, WalletId> table;

    public ConfigDrivenMerchantWalletResolver(MerchantWalletProperties props) {
        this.table = buildTable(props.merchantWallets() == null ? List.of() : props.merchantWallets());
    }

    private static Map<Key, WalletId> buildTable(List<MerchantWalletProperties.Entry> entries) {
        Map<Key, WalletId> m = new HashMap<>();
        for (MerchantWalletProperties.Entry e : entries) {
            m.put(new Key(MerchantId.of(e.merchantId()), NetworkId.parse(e.network())),
                  WalletId.of(e.walletId()));
        }
        return Map.copyOf(m);
    }

    @Override
    public WalletId resolveDepositWallet(MerchantId merchant, NetworkId network) {
        return Optional.ofNullable(table.get(new Key(merchant, network)))
            .orElseThrow(() -> new MerchantWalletNotConfiguredException(
                "no deposit wallet configured for merchant " + merchant.value()
                    + " on network " + network.canonical()));
    }
}
