package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.Balance;
import com.cpayment.custody.domain.model.NetworkId;

import java.util.List;

public interface BalancePort {
    Balance getBalance(AccountId account, AssetId asset);
    List<Balance> getBalances(AccountId account);
    Balance getBalanceByAddress(NetworkId network, AssetId asset, String address);
}
