package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.CreateWalletCommand;
import com.cpayment.custody.domain.model.Wallet;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.custody.domain.model.WalletPurpose;

import java.util.List;
import java.util.Optional;

public interface WalletPort {
    WalletId createWallet(CreateWalletCommand cmd);
    Optional<Wallet> findWallet(WalletId id);
    List<Wallet> listWalletsByPurpose(WalletPurpose purpose);
}
