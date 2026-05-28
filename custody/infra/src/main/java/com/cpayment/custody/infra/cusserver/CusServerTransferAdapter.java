package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.FeeBump;
import com.cpayment.custody.domain.model.Page;
import com.cpayment.custody.domain.model.PageRequest;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.Transfer;
import com.cpayment.custody.domain.model.TransferFilter;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.model.TransferState;
import com.cpayment.custody.domain.exception.UnsupportedCustodyOperationException;
import com.cpayment.custody.domain.port.TransferIdempotencyStore;
import com.cpayment.custody.domain.port.TransferPort;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.mapping.FeeStrategyMapper;
import com.cpayment.custody.infra.cusserver.mapping.NetworkIdMapper;
import com.cpayment.custody.infra.cusserver.rest.CusServerHttp;
import com.cpayment.custody.infra.cusserver.rest.ResilientHttpExecutor;
import com.cpayment.custody.infra.cusserver.rest.dto.CusResponse;
import com.cpayment.custody.infra.cusserver.rest.dto.SendTransactionRequestDTO;
import com.cpayment.custody.infra.cusserver.rest.dto.SendTransactionResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Real-HTTP {@link TransferPort} for cus-server.
 *
 * <h2>Idempotency</h2>
 * cus-server does NOT accept an Idempotency-Key. The adapter compensates by holding an
 * {@link TransferIdempotencyStore} keyed by the caller's {@code idempotencyKey + requestHash}:
 * <ol>
 *   <li>Look up the key. If the same hash is recorded, return the previously assigned
 *       {@link TransferId} without re-calling cus-server.</li>
 *   <li>Otherwise POST, then record the (key, hash, TransferId) tuple BEFORE returning.</li>
 * </ol>
 * Note: this is a best-effort guard. A crash between cus-server success and the record
 * call leaves a duplicate-create window for the next retry — closing it requires the
 * two-phase pattern used on the payment side. For first cut we accept the smaller
 * window and document.
 *
 * <h2>Retry</h2>
 * sendTransfer uses {@code resilient.write()} — NOT retried, only circuit-broken. POST
 * to a non-idempotent backend must never be auto-retried.
 */
@Component
public class CusServerTransferAdapter implements TransferPort {

    private static final Logger log = LoggerFactory.getLogger(CusServerTransferAdapter.class);

    private final CusServerHttp http;
    private final NetworkIdMapper networkMapper;
    private final AssetIdMapper assetMapper;
    private final FeeStrategyMapper feeMapper;
    private final TransferIdempotencyStore idempotency;
    private final ResilientHttpExecutor resilient;

    public CusServerTransferAdapter(CusServerHttp http,
                                    NetworkIdMapper networkMapper,
                                    AssetIdMapper assetMapper,
                                    FeeStrategyMapper feeMapper,
                                    TransferIdempotencyStore idempotency,
                                    ResilientHttpExecutor resilient) {
        this.http = http;
        this.networkMapper = networkMapper;
        this.assetMapper = assetMapper;
        this.feeMapper = feeMapper;
        this.idempotency = idempotency;
        this.resilient = resilient;
    }

    @Override
    public TransferId sendTransfer(SendTransferCommand cmd) {
        String hash = TransferRequestHash.of(cmd);

        // Phase 1: claim BEFORE side effect. If the COMPLETED record exists, return its
        // TransferId without re-calling cus-server. If a concurrent / crashed PENDING
        // claim exists, throw IdempotencyInProgressException — the caller must back off.
        Optional<TransferId> cached = idempotency.beginClaim(cmd.idempotencyKey(), hash);
        if (cached.isPresent()) {
            log.info("transfer.idempotent-hit key={} transferId={}",
                     cmd.idempotencyKey().value(), cached.get().value());
            return cached.get();
        }

        if (cmd.memo().isPresent()) {
            log.warn("transfer.memo-not-supported key={} memo='{}' (dropped — cus-server does not yet expose memo/tag field)",
                     cmd.idempotencyKey().value(), cmd.memo().get());
        }

        SendTransactionRequestDTO body = new SendTransactionRequestDTO(
            cmd.fromAddress(),
            cmd.toAddress(),
            cmd.amount(),
            networkMapper.toCusServer(cmd.asset().network()),
            assetMapper.toCusServer(cmd.asset()).assetName(),
            feeMapper.toCusFeeStrategy(cmd.feePreference()),
            null   // networkSpecificParams — extend later for UTXO/Tron
        );

        SendTransactionResponseDTO data;
        try {
            CusResponse<SendTransactionResponseDTO> response = resilient.write(
                () -> http.post("/api/v1/holder/transactions", body, new ParameterizedTypeReference<>() {}));
            data = http.requireData(response, "sendTransfer");
        } catch (RuntimeException preSideEffect) {
            // cus-server did NOT accept the request → safe to release the claim so a
            // retry with a fresh body or a recovered backend can proceed.
            idempotency.releaseClaim(cmd.idempotencyKey(), hash);
            throw preSideEffect;
        }

        // cus-server accepted; from here we must NEVER release the claim, even if
        // completeClaim itself fails. A retry then sees PENDING (in-progress) instead of
        // re-sending, which is strictly better than creating a duplicate transfer.
        TransferId transferId = TransferId.of(data.id());
        idempotency.completeClaim(cmd.idempotencyKey(), hash, transferId);
        return transferId;
    }

    @Override
    public TransferId speedUp(TransferId original, FeeBump bump) {
        // We do not have the original fromAddress/toAddress here — the domain calls
        // speedUp by id only. For now, this is intentionally NOT implemented; resend
        // requires re-fetching the original transfer. Adding listTransfers + filter
        // by id would let us populate the resend body. Track as a TODO.
        throw new UnsupportedCustodyOperationException(
            "speedUp not yet implemented — requires looking up the original transfer to "
                + "build a resend request");
    }

    @Override
    public Optional<Transfer> findTransfer(TransferId id) {
        // cus-server has no per-id transfer endpoint; would need listTransfers + filter.
        // Defer until a caller needs it.
        return Optional.empty();
    }

    @Override
    public Page<Transfer> listTransfers(TransferFilter filter) {
        // Defer until cpayment has a payout-listing UI use case. Returning an empty page
        // is safer than throwing — current callers don't depend on this.
        return new Page<>(java.util.List.of(),
            filter.page() == null ? 0 : filter.page().pageNumber(),
            filter.page() == null ? 50 : filter.page().pageSize(),
            0L);
    }
}
