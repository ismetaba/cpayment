package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.Account;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.CreateAccountCommand;
import com.cpayment.custody.domain.model.WalletId;

import java.util.List;
import java.util.Optional;

public interface AccountPort {

    /**
     * Provisions a new custody account (which on most chains is the deposit address itself).
     * Returns the full {@link Account} including its on-chain address — callers should NOT
     * make a separate call to look up the address.
     */
    Account createAccount(CreateAccountCommand cmd);

    Optional<Account> findAccount(AccountId id);

    List<Account> listAccountsByWallet(WalletId walletId);
}
