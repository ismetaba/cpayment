package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.Balance;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.port.BalancePort;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.rest.CusServerRestClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CusServerBalanceAdapter implements BalancePort {

    private final CusServerRestClient client;
    private final AssetIdMapper assetMapper;

    public CusServerBalanceAdapter(CusServerRestClient client, AssetIdMapper assetMapper) {
        this.client = client;
        this.assetMapper = assetMapper;
    }

    @Override
    public Balance getBalance(AccountId account, AssetId asset) {
        // TODO POST /api/v1/holder/balances with [{ network, asset: [...], address }]
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<Balance> getBalances(AccountId account) {
        // TODO POST /api/v1/holder/balances with all supported assets for the account's network
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Balance getBalanceByAddress(NetworkId network, AssetId asset, String address) {
        // TODO POST /api/v1/holder/balances with explicit address
        throw new UnsupportedOperationException("not yet implemented");
    }
}
