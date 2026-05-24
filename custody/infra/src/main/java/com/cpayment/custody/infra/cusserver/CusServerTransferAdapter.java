package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.FeeBump;
import com.cpayment.custody.domain.model.Page;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.Transfer;
import com.cpayment.custody.domain.model.TransferFilter;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferPort;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.mapping.FeeStrategyMapper;
import com.cpayment.custody.infra.cusserver.rest.CusServerRestClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CusServerTransferAdapter implements TransferPort {

    private final CusServerRestClient client;
    private final AssetIdMapper assetMapper;
    private final FeeStrategyMapper feeMapper;
    private final IdempotencyStore idempotency;

    public CusServerTransferAdapter(CusServerRestClient client, AssetIdMapper assetMapper,
                                    FeeStrategyMapper feeMapper, IdempotencyStore idempotency) {
        this.client = client;
        this.assetMapper = assetMapper;
        this.feeMapper = feeMapper;
        this.idempotency = idempotency;
    }

    @Override
    public TransferId sendTransfer(SendTransferCommand cmd) {
        // 1. Idempotency: look up (key, requestHash) -> existing TransferId, return if found.
        // 2. Map asset + fee + memo (warn if memo not supported by cus-server yet).
        // 3. POST /api/v1/holder/transactions with SendTransactionRequestDTO.
        // 4. Persist (key, requestHash, returnedId) BEFORE returning.
        // 5. Return TransferId — note: this means SUBMITTED, not BROADCAST.
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public TransferId speedUp(TransferId original, FeeBump bump) {
        // TODO POST /api/v1/holder/transactions/resend with ResendTransactionRequestDTO { replacedTxId, ...new fields }
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Optional<Transfer> findTransfer(TransferId id) {
        // TODO GET /api/v1/holder/transactions with HolderTransactionFilter id=...
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Page<Transfer> listTransfers(TransferFilter filter) {
        // TODO GET /api/v1/holder/transactions with HolderTransactionFilter
        throw new UnsupportedOperationException("not yet implemented");
    }
}
