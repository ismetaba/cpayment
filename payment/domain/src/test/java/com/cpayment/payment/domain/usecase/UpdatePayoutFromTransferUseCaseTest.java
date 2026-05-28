package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.model.PayoutStatus;
import com.cpayment.payment.domain.model.SubmittedPayout;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.domain.port.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatePayoutFromTransferUseCaseTest {

    private static final MerchantId MERCHANT = MerchantId.of(UUID.randomUUID());
    private static final AssetId USDC_ETH = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
    private static final AssetId ETH = new AssetId(new NetworkId("eth", "mainnet"), "eth");
    private static final Instant NOW = Instant.parse("2026-05-25T12:00:00Z");

    private PayoutRepository repo;
    private PayoutMutationGateway gateway;
    private UpdatePayoutFromTransferUseCase useCase;

    @BeforeEach
    void setUp() {
        repo = mock(PayoutRepository.class);
        gateway = mock(PayoutMutationGateway.class);
        useCase = new UpdatePayoutFromTransferUseCase(repo, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void onConfirmed_advances_submitted_payout_to_confirmed_and_persists() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.of(submitted(tx)));

        boolean handled = useCase.onConfirmed(new CustodyEvent.TransferConfirmed(
            tx, "0xhash", 12, BigInteger.valueOf(21_000), ETH));

        assertThat(handled).isTrue();
        assertThat(persistedStatus()).isEqualTo(PayoutStatus.CONFIRMED);
    }

    @Test
    void onBroadcast_advances_submitted_payout_to_broadcast() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.of(submitted(tx)));

        boolean handled = useCase.onBroadcast(new CustodyEvent.TransferBroadcast(tx, "0xhash"));

        assertThat(handled).isTrue();
        assertThat(persistedStatus()).isEqualTo(PayoutStatus.BROADCAST);
    }

    @Test
    void onFailed_advances_submitted_payout_to_failed() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.of(submitted(tx)));

        boolean handled = useCase.onFailed(new CustodyEvent.TransferFailed(
            tx, FailureReason.INSUFFICIENT_GAS, Optional.of("gas"), "out of gas"));

        assertThat(handled).isTrue();
        assertThat(persistedStatus()).isEqualTo(PayoutStatus.FAILED);
    }

    @Test
    void redelivery_of_confirmed_payout_is_dropped_but_reported_handled() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        Payout confirmed = submitted(tx).broadcast("0xhash", NOW)
            .confirm(12, BigInteger.valueOf(21_000), ETH, NOW);
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.of(confirmed));

        boolean handled = useCase.onConfirmed(new CustodyEvent.TransferConfirmed(
            tx, "0xhash", 13, BigInteger.valueOf(21_000), ETH));

        assertThat(handled).isTrue();                  // matched
        verify(gateway, never()).apply(any(), anyList()); // but dropped
    }

    @Test
    void no_matching_payout_returns_false() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.empty());

        boolean handled = useCase.onConfirmed(new CustodyEvent.TransferConfirmed(
            tx, "0xhash", 12, BigInteger.valueOf(21_000), ETH));

        assertThat(handled).isFalse();
        verify(gateway, never()).apply(any(), anyList());
    }

    private PayoutStatus persistedStatus() {
        ArgumentCaptor<Payout> captor = ArgumentCaptor.forClass(Payout.class);
        verify(gateway).apply(captor.capture(), anyList());
        return captor.getValue().status();
    }

    private SubmittedPayout submitted(TransferId tx) {
        return SubmittedPayout.fresh(PayoutId.newId(), MERCHANT, USDC_ETH,
            "0xFROM", "0xTO", BigInteger.valueOf(1_000_000), Optional.empty(), tx, NOW);
    }
}
