package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.exception.PayoutNotCancellableException;
import com.cpayment.payment.domain.exception.PayoutNotFoundException;
import com.cpayment.payment.domain.model.BroadcastPayout;
import com.cpayment.payment.domain.model.CancelledPayout;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutEvent;
import com.cpayment.payment.domain.model.PayoutEventType;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CancelPayoutUseCaseTest {

    private static final AssetId USDC_ETH = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
    private static final Instant FIXED_NOW = Instant.parse("2026-05-26T08:00:00Z");

    private PayoutRepository payouts;
    private PayoutMutationGateway gateway;
    private CancelPayoutUseCase useCase;

    @BeforeEach
    void setUp() {
        payouts = mock(PayoutRepository.class);
        gateway = mock(PayoutMutationGateway.class);
        useCase = new CancelPayoutUseCase(payouts, gateway, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void cancels_a_submitted_payout_and_emits_PAYOUT_CANCELLED() {
        SubmittedPayout submitted = sampleSubmitted();
        when(payouts.findById(submitted.id())).thenReturn(Optional.of(submitted));

        Payout result = useCase.execute(submitted.id());

        assertThat(result).isInstanceOf(CancelledPayout.class);
        assertThat(result.status()).isEqualTo(PayoutStatus.CANCELLED);
        assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PayoutEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(gateway).apply(eq(result), eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasSize(1);
        assertThat(eventsCaptor.getValue().get(0).type()).isEqualTo(PayoutEventType.PAYOUT_CANCELLED);
    }

    @Test
    void cancelling_an_already_cancelled_payout_is_a_no_op() {
        CancelledPayout already = sampleSubmitted().cancel(FIXED_NOW.minusSeconds(120));
        when(payouts.findById(already.id())).thenReturn(Optional.of(already));

        Payout result = useCase.execute(already.id());

        assertThat(result).isEqualTo(already);
        verify(gateway, never()).apply(any(), any());
    }

    @Test
    void rejects_cancellation_when_broadcast_already() {
        BroadcastPayout broadcast = sampleSubmitted().broadcast("0xdeadbeef", FIXED_NOW.minusSeconds(60));
        when(payouts.findById(broadcast.id())).thenReturn(Optional.of(broadcast));

        assertThatThrownBy(() -> useCase.execute(broadcast.id()))
            .isInstanceOf(PayoutNotCancellableException.class)
            .hasMessageContaining("BROADCAST");

        verify(gateway, never()).apply(any(), any());
    }

    @Test
    void rejects_cancellation_when_already_failed() {
        var failed = sampleSubmitted().fail(FailureReason.NETWORK_REJECTED, "boom", FIXED_NOW.minusSeconds(60));
        when(payouts.findById(failed.id())).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> useCase.execute(failed.id()))
            .isInstanceOf(PayoutNotCancellableException.class)
            .hasMessageContaining("FAILED");
    }

    @Test
    void rejects_when_payout_unknown() {
        PayoutId id = PayoutId.newId();
        when(payouts.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
            .isInstanceOf(PayoutNotFoundException.class);

        verify(gateway, never()).apply(any(), any());
    }

    private SubmittedPayout sampleSubmitted() {
        return SubmittedPayout.fresh(
            PayoutId.newId(),
            MerchantId.of(UUID.randomUUID()),
            USDC_ETH,
            "0xFROM", "0xTO",
            BigInteger.valueOf(500_000),
            Optional.empty(),
            TransferId.of(UUID.randomUUID()),
            FIXED_NOW.minusSeconds(180)
        );
    }
}
