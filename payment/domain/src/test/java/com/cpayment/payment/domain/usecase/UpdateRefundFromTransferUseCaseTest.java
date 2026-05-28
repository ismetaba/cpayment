package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.payment.domain.model.IssuedRefund;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundId;
import com.cpayment.payment.domain.model.RefundReason;
import com.cpayment.payment.domain.model.RefundStatus;
import com.cpayment.payment.domain.port.RefundMutationGateway;
import com.cpayment.payment.domain.port.RefundRepository;
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

class UpdateRefundFromTransferUseCaseTest {

    private static final MerchantId MERCHANT = MerchantId.of(UUID.randomUUID());
    private static final AssetId USDC_ETH = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
    private static final AssetId ETH = new AssetId(new NetworkId("eth", "mainnet"), "eth");
    private static final Instant NOW = Instant.parse("2026-05-25T12:00:00Z");

    private RefundRepository repo;
    private RefundMutationGateway gateway;
    private UpdateRefundFromTransferUseCase useCase;

    @BeforeEach
    void setUp() {
        repo = mock(RefundRepository.class);
        gateway = mock(RefundMutationGateway.class);
        useCase = new UpdateRefundFromTransferUseCase(repo, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void onConfirmed_advances_issued_refund_through_broadcast_to_confirmed() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.of(issued(tx)));

        boolean handled = useCase.onConfirmed(new CustodyEvent.TransferConfirmed(
            tx, "0xhash", 6, BigInteger.valueOf(1000), ETH));

        assertThat(handled).isTrue();
        assertThat(persistedStatus()).isEqualTo(RefundStatus.CONFIRMED);
    }

    @Test
    void onFailed_advances_issued_refund_to_failed() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.of(issued(tx)));

        boolean handled = useCase.onFailed(new CustodyEvent.TransferFailed(
            tx, FailureReason.POLICY_REJECTED, Optional.of("policy"), "rejected"));

        assertThat(handled).isTrue();
        assertThat(persistedStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void redelivery_of_confirmed_refund_is_dropped_but_reported_handled() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        Refund confirmed = issued(tx).broadcast("0xhash", NOW)
            .confirm(6, BigInteger.valueOf(1000), ETH, NOW);
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.of(confirmed));

        boolean handled = useCase.onConfirmed(new CustodyEvent.TransferConfirmed(
            tx, "0xhash", 7, BigInteger.valueOf(1000), ETH));

        assertThat(handled).isTrue();
        verify(gateway, never()).apply(any(), anyList());
    }

    @Test
    void no_matching_refund_returns_false() {
        TransferId tx = TransferId.of(UUID.randomUUID());
        when(repo.findByCustodyTransferId(tx)).thenReturn(Optional.empty());

        boolean handled = useCase.onConfirmed(new CustodyEvent.TransferConfirmed(
            tx, "0xhash", 6, BigInteger.valueOf(1000), ETH));

        assertThat(handled).isFalse();
        verify(gateway, never()).apply(any(), anyList());
    }

    private RefundStatus persistedStatus() {
        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(gateway).apply(captor.capture(), anyList());
        return captor.getValue().status();
    }

    private IssuedRefund issued(TransferId tx) {
        return IssuedRefund.fresh(RefundId.newId(), InvoiceId.newId(), MERCHANT, USDC_ETH,
            "0xFROM", "0xTO", BigInteger.valueOf(500_000), RefundReason.REQUESTED_BY_CUSTOMER,
            Optional.empty(), tx, NOW);
    }
}
