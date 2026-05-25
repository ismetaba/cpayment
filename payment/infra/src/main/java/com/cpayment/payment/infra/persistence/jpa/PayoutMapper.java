package com.cpayment.payment.infra.persistence.jpa;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.BroadcastPayout;
import com.cpayment.payment.domain.model.CancelledPayout;
import com.cpayment.payment.domain.model.ConfirmedPayout;
import com.cpayment.payment.domain.model.FailedPayout;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.model.PayoutStatus;
import com.cpayment.payment.domain.model.ReplacedPayout;
import com.cpayment.payment.domain.model.SubmittedPayout;

import java.util.Optional;
import java.util.UUID;

/**
 * Bidirectional mapping between {@link Payout} variants and the single
 * {@link PayoutEntity} row.
 *
 * <p>The persistence table is flat — it holds every nullable column the domain
 * variants <em>could</em> use — but the read side dispatches on
 * {@code PayoutStatus} to pick the right variant constructor, and the write side
 * switches on the sealed type so the exhaustive compiler check catches any new
 * variant that forgets to map a field.
 */
final class PayoutMapper {

    private PayoutMapper() {}

    static PayoutEntity toEntity(Payout payout) {
        PayoutEntity e = baseEntity(payout);

        switch (payout) {
            case SubmittedPayout s -> {
                e.setStatus(PayoutStatus.SUBMITTED);
                e.setCustodyTransferId(s.custodyTransferId().value());
            }
            case BroadcastPayout b -> {
                e.setStatus(PayoutStatus.BROADCAST);
                e.setCustodyTransferId(b.custodyTransferId().value());
                e.setTxHash(b.txHash());
            }
            case ConfirmedPayout c -> {
                e.setStatus(PayoutStatus.CONFIRMED);
                e.setCustodyTransferId(c.custodyTransferId().value());
                e.setTxHash(c.txHash());
                e.setConfirmations(c.confirmations());
                e.setFeeActual(c.feeActual());
                e.setFeeAssetCanonical(c.feeAsset().canonical());
            }
            case FailedPayout f -> {
                e.setStatus(PayoutStatus.FAILED);
                e.setCustodyTransferId(f.custodyTransferId().value());
                f.txHash().ifPresent(e::setTxHash);
                e.setFailureReason(f.failureReason().name());
                e.setFailureMessage(f.failureMessage());
            }
            case ReplacedPayout r -> {
                e.setStatus(PayoutStatus.REPLACED);
                e.setCustodyTransferId(r.custodyTransferId().value());
                e.setReplacedBy(r.replacedBy().value());
            }
            case CancelledPayout x -> {
                e.setStatus(PayoutStatus.CANCELLED);
                e.setCustodyTransferId(x.custodyTransferId().value());
            }
        }
        return e;
    }

    static Payout toDomain(PayoutEntity e) {
        PayoutId id = PayoutId.of(e.getId());
        MerchantId merchantId = MerchantId.of(e.getMerchantId());
        AssetId asset = AssetId.parse(e.getAssetCanonical());
        Optional<String> memo = Optional.ofNullable(e.getMemo());
        TransferId transferId = TransferId.of(requireTransferId(e));

        return switch (e.getStatus()) {
            case SUBMITTED -> new SubmittedPayout(id, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), memo,
                transferId, e.getCreatedAt(), e.getUpdatedAt());

            case BROADCAST -> new BroadcastPayout(id, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), memo,
                transferId, requireNonBlank(e.getTxHash(), "txHash", e),
                e.getCreatedAt(), e.getUpdatedAt());

            case CONFIRMED -> new ConfirmedPayout(id, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), memo,
                transferId, requireNonBlank(e.getTxHash(), "txHash", e),
                requireNonNull(e.getConfirmations(), "confirmations", e),
                requireNonNull(e.getFeeActual(), "feeActual", e),
                AssetId.parse(requireNonBlank(e.getFeeAssetCanonical(), "feeAsset", e)),
                e.getCreatedAt(), e.getUpdatedAt());

            case FAILED -> new FailedPayout(id, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), memo,
                transferId, Optional.ofNullable(e.getTxHash()),
                FailureReason.valueOf(requireNonBlank(e.getFailureReason(), "failureReason", e)),
                e.getFailureMessage() != null ? e.getFailureMessage() : "(none)",
                e.getCreatedAt(), e.getUpdatedAt());

            case REPLACED -> new ReplacedPayout(id, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), memo,
                transferId, TransferId.of(requireNonNull(e.getReplacedBy(), "replacedBy", e)),
                e.getCreatedAt(), e.getUpdatedAt());

            case CANCELLED -> new CancelledPayout(id, merchantId, asset,
                e.getFromAddress(), e.getToAddress(), e.getAmount(), memo,
                transferId, e.getCreatedAt(), e.getUpdatedAt());

            case REQUESTED -> throw new IllegalStateException(
                "PayoutEntity " + e.getId() + " has unsupported persisted status REQUESTED");
        };
    }

    private static PayoutEntity baseEntity(Payout p) {
        PayoutEntity e = new PayoutEntity();
        e.setId(p.id().value());
        e.setMerchantId(p.merchantId().value());
        e.setAssetCanonical(p.asset().canonical());
        e.setFromAddress(p.fromAddress());
        e.setToAddress(p.toAddress());
        e.setAmount(p.amount());
        e.setMemo(p.memo().orElse(null));
        e.setCreatedAt(p.createdAt());
        e.setUpdatedAt(p.updatedAt());
        return e;
    }

    private static UUID requireTransferId(PayoutEntity e) {
        return EntityFieldGuards.requireNonNull(
            e.getCustodyTransferId(), "custody_transfer_id", "PayoutEntity " + e.getId(), e.getStatus());
    }

    private static <T> T requireNonNull(T value, String field, PayoutEntity e) {
        return EntityFieldGuards.requireNonNull(value, field, "PayoutEntity " + e.getId(), e.getStatus());
    }

    private static String requireNonBlank(String value, String field, PayoutEntity e) {
        return EntityFieldGuards.requireNonBlank(value, field, "PayoutEntity " + e.getId(), e.getStatus());
    }
}
