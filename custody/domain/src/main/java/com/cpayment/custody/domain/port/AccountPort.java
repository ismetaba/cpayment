package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.Account;
import com.cpayment.custody.domain.model.CreateAccountCommand;
import com.cpayment.custody.domain.model.WalletId;

import java.util.List;

/**
 * Account provisioning + listing.
 *
 * <p>A per-id read ({@code findAccount(AccountId)}) is deliberately NOT on this port:
 * cus-server does not offer such an endpoint, and synthesising it via list-and-filter
 * would be O(n) and silently slow as wallets grow. If a future provider supports it,
 * declare a separate {@code AccountLookupPort} and let cpayment depend on it
 * conditionally (capability flag). LSP &gt; convenience.
 */
public interface AccountPort {

    /**
     * Provisions a new custody account (which on most chains is the deposit address itself).
     * Returns the full {@link Account} including its on-chain address — callers should NOT
     * make a separate call to look up the address.
     */
    Account createAccount(CreateAccountCommand cmd);

    List<Account> listAccountsByWallet(WalletId walletId);
}
