package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.exception.UnsupportedCustodyOperationException;
import com.cpayment.custody.domain.model.CreateWalletCommand;
import com.cpayment.custody.domain.model.Wallet;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.custody.domain.model.WalletPurpose;
import com.cpayment.custody.domain.port.WalletPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link WalletPort} for cus-server. cus-server's wallet APIs are not yet wired; every
 * operation fails fast with {@link UnsupportedCustodyOperationException} so a caller that
 * reaches this adapter without checking {@code Capabilities} gets a clear, typed error
 * rather than a generic {@code UnsupportedOperationException}.
 */
@Component
public class CusServerWalletAdapter implements WalletPort {

    @Override
    public WalletId createWallet(CreateWalletCommand cmd) {
        // TODO POST /api/v1/holder/wallets with CreateWalletRequestDTO { masterWalletId, walletLabel }
        // - resolve masterWalletId for cpayment's holder context
        // - store purpose locally (cus-server doesn't carry purpose metadata; cpayment owns the mapping)
        throw new UnsupportedCustodyOperationException("createWallet is not yet implemented for cus-server");
    }

    @Override
    public Optional<Wallet> findWallet(WalletId id) {
        // TODO GET /api/v1/holder/wallets?id={id} (filter via HolderWalletFilter)
        throw new UnsupportedCustodyOperationException("findWallet is not yet implemented for cus-server");
    }

    @Override
    public List<Wallet> listWalletsByPurpose(WalletPurpose purpose) {
        // TODO: cus-server has no purpose concept; cpayment must keep its own wallet_purpose table
        // and join against cus-server's wallet list
        throw new UnsupportedCustodyOperationException(
            "listWalletsByPurpose is not yet implemented for cus-server");
    }
}
