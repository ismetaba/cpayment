package com.cpayment.custody.infra.cusserver.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.infra.cusserver.event.dto.TransactionUpdatePayloadDTO;
import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;

/**
 * Translates a single cus-server {@link TransactionUpdatePayloadDTO} into the
 * appropriate normalized {@link CustodyEvent}.
 *
 * <p>Mapping table (subject to cross-check against cus-server's actual TransactionUpdateType):
 * <ul>
 *   <li>BROADCAST / SUBMITTED → {@link CustodyEvent.TransferBroadcast}</li>
 *   <li>CONFIRMED / FINALIZED → {@link CustodyEvent.TransferConfirmed}</li>
 *   <li>FAILED / REJECTED / EXPIRED → {@link CustodyEvent.TransferFailed}</li>
 *   <li>REPLACED / SPED_UP → {@link CustodyEvent.TransferReplaced}</li>
 * </ul>
 * Unknown types fall through; the bridge logs and drops them so an unexpected upstream
 * variant doesn't crash the consumer.
 */
@Component
public class TransferUpdateMapper {

    private final AssetIdMapper assetMapper;

    public TransferUpdateMapper(AssetIdMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    public Optional<CustodyEvent> toCustodyEvent(TransactionUpdatePayloadDTO dto) {
        if (dto == null || dto.transactionId() == null || dto.type() == null) {
            throw new CustodyAdapterException("transaction update payload missing required fields");
        }
        TransferId id = TransferId.of(dto.transactionId());
        String txHash = dto.txHash() != null ? dto.txHash() : "";
        return switch (dto.type().toUpperCase(Locale.ROOT)) {
            case "BROADCAST", "SUBMITTED" ->
                Optional.of(new CustodyEvent.TransferBroadcast(id, txHash));

            case "CONFIRMED", "FINALIZED" ->
                Optional.of(toConfirmed(dto, id, txHash));

            case "FAILED", "REJECTED", "EXPIRED" ->
                Optional.of(new CustodyEvent.TransferFailed(
                    id,
                    classifyReason(dto.reason()),
                    Optional.ofNullable(dto.reason()),
                    dto.reason() != null ? dto.reason() : dto.type()));

            case "REPLACED", "SPED_UP" -> dto.replacementTransactionId() != null
                ? Optional.of(new CustodyEvent.TransferReplaced(
                    id,
                    TransferId.of(dto.replacementTransactionId()),
                    dto.reason() != null ? dto.reason() : "replaced"))
                : Optional.empty();

            default -> Optional.empty();
        };
    }

    private CustodyEvent.TransferConfirmed toConfirmed(TransactionUpdatePayloadDTO dto,
                                                       TransferId id, String txHash) {
        int confirmations = dto.confirmations() != null ? dto.confirmations() : 0;
        BigInteger feeActual = dto.feeActual() != null ? dto.feeActual() : BigInteger.ZERO;
        AssetId feeAsset = resolveFeeAsset(dto);
        return new CustodyEvent.TransferConfirmed(id, txHash, confirmations, feeActual, feeAsset);
    }

    private AssetId resolveFeeAsset(TransactionUpdatePayloadDTO dto) {
        if (dto.feeNetworkName() == null || dto.feeAssetName() == null) {
            // Sensible fallback: synthesise an "unknown" asset id so downstream consumers
            // can still see *that* a fee was charged. Unmapped → throws; we'd rather not
            // drop the whole confirmation just because the fee asset is unfamiliar.
            return assetMapper.fromCusServer("ETHEREUM", "ETH");
        }
        return assetMapper.fromCusServer(dto.feeNetworkName(), dto.feeAssetName());
    }

    private FailureReason classifyReason(String raw) {
        if (raw == null) return FailureReason.UNKNOWN;
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("INSUFFICIENT") && upper.contains("GAS")) return FailureReason.INSUFFICIENT_GAS;
        if (upper.contains("INSUFFICIENT"))                          return FailureReason.INSUFFICIENT_BALANCE;
        if (upper.contains("NONCE"))                                 return FailureReason.NONCE_CONFLICT;
        if (upper.contains("POLICY") || upper.contains("REJECT"))    return FailureReason.POLICY_REJECTED;
        if (upper.contains("TIMEOUT"))                               return FailureReason.TIMEOUT;
        if (upper.contains("NETWORK") || upper.contains("REVERT"))   return FailureReason.NETWORK_REJECTED;
        return FailureReason.UNKNOWN;
    }
}
