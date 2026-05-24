package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.CreateWalletCommand;
import com.cpayment.custody.domain.model.Wallet;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.custody.domain.model.WalletPurpose;
import com.cpayment.custody.domain.port.WalletPort;
import com.cpayment.custody.infra.cusserver.rest.CusServerRestClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CusServerWalletAdapter implements WalletPort {

    private final CusServerRestClient client;

    public CusServerWalletAdapter(CusServerRestClient client) {
        this.client = client;
    }

    @Override
    public WalletId createWallet(CreateWalletCommand cmd) {
        // TODO POST /api/v1/holder/wallets with CreateWalletRequestDTO { masterWalletId, walletLabel }
        // - resolve masterWalletId for cpayment's holder context
        // - store purpose locally (cus-server doesn't carry purpose metadata; cpayment owns the mapping)
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Optional<Wallet> findWallet(WalletId id) {
        // TODO GET /api/v1/holder/wallets?id={id} (filter via HolderWalletFilter)
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<Wallet> listWalletsByPurpose(WalletPurpose purpose) {
        // TODO: cus-server has no purpose concept; cpayment must keep its own wallet_purpose table
        // and join against cus-server's wallet list
        throw new UnsupportedOperationException("not yet implemented");
    }
}
