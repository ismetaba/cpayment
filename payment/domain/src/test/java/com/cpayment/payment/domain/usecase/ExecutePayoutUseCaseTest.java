package com.cpayment.payment.domain.usecase;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferPort;
import com.cpayment.payment.domain.model.CreatePayoutCommand;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutCreatedResult;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.PayoutEventType;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.model.PayoutStatus;
import com.cpayment.payment.domain.port.PayoutIdempotencyStore;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutePayoutUseCaseTest {

    private static final MerchantId MERCHANT = MerchantId.of(UUID.randomUUID());
    private static final AssetId USDC_ETH = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
    private static final IdempotencyKey KEY = IdempotencyKey.of("payout-key-1");
    private static final Instant FIXED_NOW = Instant.parse("2026-05-25T12:00:00Z");

    private PayoutIdempotencyStore idempotency;
    private PayoutMutationGateway gateway;
    private TransferPort transfers;
    private ExecutePayoutUseCase useCase;

    @BeforeEach
    void setUp() {
        idempotency = mock(PayoutIdempotencyStore.class);
        gateway = mock(PayoutMutationGateway.class);
        transfers = mock(TransferPort.class);
        useCase = new ExecutePayoutUseCase(idempotency, gateway, transfers,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void submits_to_custody_and_emits_PAYOUT_SUBMITTED() {
        TransferId transferId = TransferId.of(UUID.randomUUID());
        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(transfers.sendTransfer(any(SendTransferCommand.class))).thenReturn(transferId);

        PayoutCreatedResult result = useCase.execute(command(BigInteger.valueOf(1_000_000)));

        Payout p = result.payout();
        assertThat(p.status()).isEqualTo(PayoutStatus.SUBMITTED);
        assertThat(p.custodyTransferId()).contains(transferId);

        ArgumentCaptor<List<PayoutEvent>> eventsCaptor = captor();
        verify(gateway).apply(any(Payout.class), eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasSize(1);
        assertThat(eventsCaptor.getValue().get(0).type()).isEqualTo(PayoutEventType.PAYOUT_SUBMITTED);

        verify(idempotency).completeClaim(eq(KEY), any(String.class), any(Payout.class));
        verify(idempotency, never()).releaseClaim(any(), any());
    }

    @Test
    void idempotent_hit_returns_cached_payout_without_side_effects() {
        Payout cached = samplePayout();
        when(idempotency.beginClaim(eq(KEY), any(String.class))).thenReturn(Optional.of(cached));

        PayoutCreatedResult result = useCase.execute(command(BigInteger.valueOf(1_000_000)));

        assertThat(result.payout()).isEqualTo(cached);
        verifyNoInteractions(transfers, gateway);
        verify(idempotency, never()).completeClaim(any(), any(), any());
    }

    @Test
    void conflict_from_begin_propagates_without_side_effects() {
        when(idempotency.beginClaim(eq(KEY), any(String.class)))
            .thenThrow(new IdempotencyConflictException("different body"));

        assertThatThrownBy(() -> useCase.execute(command(BigInteger.valueOf(1_000_000))))
            .isInstanceOf(IdempotencyConflictException.class);

        verifyNoInteractions(transfers, gateway);
    }

    @Test
    void releases_claim_when_custody_send_fails() {
        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(transfers.sendTransfer(any())).thenThrow(new RuntimeException("cus-server down"));

        assertThatThrownBy(() -> useCase.execute(command(BigInteger.valueOf(1_000_000))))
            .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(gateway);
        verify(idempotency).releaseClaim(eq(KEY), any(String.class));
        verify(idempotency, never()).completeClaim(any(), any(), any());
    }

    @Test
    void does_NOT_release_claim_when_save_fails_after_custody_send() {
        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(transfers.sendTransfer(any())).thenReturn(TransferId.of(UUID.randomUUID()));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
            .when(gateway).apply(any(), any());

        assertThatThrownBy(() -> useCase.execute(command(BigInteger.valueOf(1_000_000))))
            .isInstanceOf(RuntimeException.class);

        verify(idempotency, never()).releaseClaim(any(), any());
        verify(idempotency, never()).completeClaim(any(), any(), any());
    }

    private CreatePayoutCommand command(BigInteger amount) {
        return new CreatePayoutCommand(KEY, MERCHANT, USDC_ETH,
            "0xFROM", "0xTO", amount, Optional.empty());
    }

    private Payout samplePayout() {
        return Payout.requested(PayoutId.newId(), MERCHANT, USDC_ETH,
            "0xFROM", "0xTO", BigInteger.valueOf(1_000_000), Optional.empty(), FIXED_NOW);
    }

    private static <T> T eq(T t) { return org.mockito.ArgumentMatchers.eq(t); }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentCaptor<List<T>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }

    // unused import suppressor
    @SuppressWarnings("unused") private static void touch() { AccountId.of(UUID.randomUUID()); }
}
