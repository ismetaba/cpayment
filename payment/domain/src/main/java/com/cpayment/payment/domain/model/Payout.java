package com.cpayment.payment.domain.model;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.TransferId;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

/**
 * Payout aggregate, modelled as a typestate machine. Each lifecycle state is a
 * distinct record so the compiler enforces "what fields exist when".
 *
 * <h2>Variants</h2>
 * <ul>
 *   <li>{@link SubmittedPayout}  — cus-server accepted the request and returned a
 *       {@link TransferId}; not yet on-chain.</li>
 *   <li>{@link BroadcastPayout}  — has a tx hash (mempool / first confirmation).</li>
 *   <li>{@link ConfirmedPayout}  — terminal success; has confirmations + actual fee.</li>
 *   <li>{@link FailedPayout}     — terminal failure; has reason + message. txHash is
 *       optional because failure may occur pre- or post-broadcast.</li>
 *   <li>{@link ReplacedPayout}   — terminal; superseded by a new transfer (RBF / speed-up).</li>
 *   <li>{@link CancelledPayout}  — terminal; operator-cancelled before broadcast.</li>
 * </ul>
 *
 * <h2>Why no {@code REQUESTED} variant?</h2>
 * <p>{@code REQUESTED} was an internal state in the previous model that never persisted —
 * the create use case transitioned REQUESTED → SUBMITTED within a single call. Modelling
 * an unreachable state added noise, so it's elided. The {@link PayoutStatus} enum keeps
 * the value for backward-compat in the schema.
 *
 * <h2>Common contract</h2>
 * <p>Every variant carries the identity-and-money fields ({@link #id()}, {@link #merchantId()},
 * {@link #asset()}, {@link #amount()}…) plus a NON-optional {@link #custodyTransferId()} —
 * every persisted variant has been accepted by cus-server, so the upstream id is always
 * known.
 */
public sealed interface Payout
    permits SubmittedPayout, BroadcastPayout, ConfirmedPayout,
            FailedPayout, ReplacedPayout, CancelledPayout {

    PayoutId id();
    MerchantId merchantId();
    AssetId asset();
    String fromAddress();
    String toAddress();
    BigInteger amount();
    Optional<String> memo();
    TransferId custodyTransferId();
    Instant createdAt();
    Instant updatedAt();

    /** Enum projection — used for DB serialisation and the API/webhook payload. */
    PayoutStatus status();
}
