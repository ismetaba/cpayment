package com.cpayment.payment.domain.port;

import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.payment.domain.model.MerchantId;

/**
 * Outbound port — resolves which custody wallet hosts a merchant's deposit accounts
 * on a given network.
 *
 * <p>This isolation lets the policy of "one wallet per merchant" vs "one wallet per network
 * shared across merchants" be swapped without touching use cases. Initial implementation is
 * config-driven; production may join a merchant_wallet table.
 */
public interface MerchantWalletResolver {

    WalletId resolveDepositWallet(MerchantId merchant, NetworkId network);
}
