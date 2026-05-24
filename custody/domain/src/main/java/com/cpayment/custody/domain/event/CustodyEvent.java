package com.cpayment.custody.domain.event;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Provider-agnostic, normalized custody event.
 * Adapter is responsible for translating provider-native events (e.g. cus-server's
 * CreateDepositTransactionsEvent) into one of these. Unknown event types are
 * dropped at the adapter boundary, NOT surfaced as a catch-all variant.
 */
public sealed interface CustodyEvent permits
    CustodyEvent.DepositDetected,
    CustodyEvent.DepositConfirmed,
    CustodyEvent.TransferBroadcast,
    CustodyEvent.TransferConfirmed,
    CustodyEvent.TransferFailed,
    CustodyEvent.TransferReplaced,
    CustodyEvent.ApprovalPending,
    CustodyEvent.ApprovalGranted,
    CustodyEvent.ApprovalRejected,
    CustodyEvent.BalanceStale {

    record DepositDetected(
        AccountId account, String fromAddress, AssetId asset,
        BigInteger amount, String txHash, int confirmationsSoFar
    ) implements CustodyEvent {}

    record DepositConfirmed(
        AccountId account, String fromAddress, AssetId asset,
        BigInteger amount, String txHash, int confirmations
    ) implements CustodyEvent {}

    record TransferBroadcast(TransferId id, String txHash) implements CustodyEvent {}

    record TransferConfirmed(
        TransferId id, String txHash, int confirmations,
        BigInteger feeActual, AssetId feeAsset
    ) implements CustodyEvent {}

    record TransferFailed(
        TransferId id, FailureReason reason,
        Optional<String> providerCode, String message
    ) implements CustodyEvent {}

    record TransferReplaced(TransferId oldId, TransferId newId, String reason) implements CustodyEvent {}

    record ApprovalPending(TransferId id, String reason) implements CustodyEvent {}
    record ApprovalGranted(TransferId id, String approverRef) implements CustodyEvent {}
    record ApprovalRejected(TransferId id, String approverRef, String reason) implements CustodyEvent {}

    record BalanceStale(AccountId account, AssetId asset) implements CustodyEvent {}
}
