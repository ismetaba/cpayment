package com.cpayment.custody.infra.cusserver.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.infra.cusserver.event.dto.DepositTransactionDTO;
import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.mapping.NetworkIdMapper;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * Pure mapping between a cus-server deposit DTO and the normalized domain event.
 * Stateless and side-effect free — easy to unit-test in isolation.
 */
@Component
public class DepositEventMapper {

    private final NetworkIdMapper networkMapper;
    private final AssetIdMapper assetMapper;

    public DepositEventMapper(NetworkIdMapper networkMapper, AssetIdMapper assetMapper) {
        this.networkMapper = networkMapper;
        this.assetMapper = assetMapper;
    }

    public CustodyEvent.DepositDetected toDepositDetected(DepositTransactionDTO dto) {
        if (dto == null || dto.accountId() == null) {
            throw new CustodyAdapterException("deposit event payload missing accountId");
        }
        NetworkId network = networkMapper.fromCusServer(dto.networkName());
        AssetId asset = assetMapper.fromCusServer(dto.networkName(), dto.assetName());
        BigInteger amount = dto.amount() == null ? BigInteger.ZERO : dto.amount();
        int confirmations = dto.confirmations() == null ? 0 : dto.confirmations();
        String txHash = dto.txHash() == null ? "" : dto.txHash();

        // network parameter intentionally validated above but not stored in the event —
        // network is derivable from AssetId and we avoid widening DepositDetected for it.
        if (!asset.network().equals(network)) {
            throw new CustodyAdapterException(
                "deposit event asset/network mismatch: asset=" + asset.canonical()
                    + " network=" + network.canonical());
        }

        return new CustodyEvent.DepositDetected(
            AccountId.of(dto.accountId()),
            dto.fromAddress() == null ? "" : dto.fromAddress(),
            asset,
            amount,
            txHash,
            confirmations
        );
    }
}
