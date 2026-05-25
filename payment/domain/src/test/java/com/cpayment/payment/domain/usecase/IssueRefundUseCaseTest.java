package com.cpayment.payment.domain.usecase;

import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.TransferPort;
import com.cpayment.payment.domain.exception.InvoiceNotFoundException;
import com.cpayment.payment.domain.exception.InvoiceNotRefundableException;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.InvoiceStatus;
import com.cpayment.payment.domain.model.IssueRefundCommand;
import com.cpayment.payment.domain.model.IssuedRefund;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.RefundReason;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.RefundIdempotencyStore;
import com.cpayment.payment.domain.port.RefundMutationGateway;
import com.cpayment.payment.domain.port.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IssueRefundUseCaseTest {

    private static final AssetId USDC_ETH = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
    private static final IdempotencyKey KEY = IdempotencyKey.of("refund-key-1");
    private static final Instant FIXED_NOW = Instant.parse("2026-05-27T08:00:00Z");

    private InvoiceRepository invoices;
    private RefundRepository refunds;
    private RefundIdempotencyStore idempotency;
    private RefundMutationGateway gateway;
    private TransferPort transfers;
    private IssueRefundUseCase useCase;

    @BeforeEach
    void setUp() {
        invoices = mock(InvoiceRepository.class);
        refunds = mock(RefundRepository.class);
        idempotency = mock(RefundIdempotencyStore.class);
        gateway = mock(RefundMutationGateway.class);
        transfers = mock(TransferPort.class);
        useCase = new IssueRefundUseCase(invoices, refunds, idempotency, gateway, transfers,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void issues_refund_for_paid_invoice_emits_REFUND_ISSUED() {
        Invoice paid = paidInvoice(BigInteger.valueOf(1_000_000));
        TransferId transferId = TransferId.of(UUID.randomUUID());

        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(invoices.findById(paid.id())).thenReturn(Optional.of(paid));
        when(refunds.sumIssuedNonFailed(paid.id())).thenReturn(BigInteger.ZERO);
        when(transfers.sendTransfer(any(SendTransferCommand.class))).thenReturn(transferId);

        var result = useCase.execute(command(paid.id(), BigInteger.valueOf(400_000)));

        assertThat(result.refund()).isInstanceOf(IssuedRefund.class);
        assertThat(result.refund().custodyTransferId()).isEqualTo(transferId);
        assertThat(result.refund().invoiceId()).isEqualTo(paid.id());

        verify(gateway).apply(any(), any());
        verify(idempotency).completeClaim(eq(KEY), any(String.class), any());
        verify(idempotency, never()).releaseClaim(any(), any());
    }

    @Test
    void idempotent_hit_returns_cached_refund() {
        Invoice paid = paidInvoice(BigInteger.valueOf(1_000_000));
        IssuedRefund cached = sampleIssued(paid);
        when(idempotency.beginClaim(eq(KEY), any())).thenReturn(Optional.of(cached));

        var result = useCase.execute(command(paid.id(), BigInteger.valueOf(400_000)));

        assertThat(result.refund()).isEqualTo(cached);
        verifyNoInteractions(invoices, refunds, transfers, gateway);
    }

    @Test
    void rejects_when_invoice_missing() {
        InvoiceId id = InvoiceId.newId();
        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(invoices.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command(id, BigInteger.valueOf(100))))
            .isInstanceOf(InvoiceNotFoundException.class);

        verify(idempotency).releaseClaim(eq(KEY), any());
        verifyNoInteractions(transfers, gateway);
    }

    @Test
    void rejects_when_invoice_not_paid_AND_releases_claim() {
        Invoice pending = Invoice.newlyCreated(InvoiceId.newId(), MerchantId.of(UUID.randomUUID()),
            USDC_ETH, BigInteger.valueOf(1_000_000), 12,
            AccountId.of(UUID.randomUUID()), "0xADDR", FIXED_NOW.minusSeconds(600));
        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(invoices.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.execute(command(pending.id(), BigInteger.valueOf(100))))
            .isInstanceOf(InvoiceNotRefundableException.class)
            .hasMessageContaining("AWAITING_DEPOSIT");

        verify(idempotency).releaseClaim(eq(KEY), any());  // validation failure MUST release the claim
        verifyNoInteractions(transfers, gateway);
    }

    @Test
    void rejects_when_total_refunded_would_exceed_invoice_AND_releases_claim() {
        Invoice paid = paidInvoice(BigInteger.valueOf(1_000_000));
        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(invoices.findById(paid.id())).thenReturn(Optional.of(paid));
        when(refunds.sumIssuedNonFailed(paid.id())).thenReturn(BigInteger.valueOf(700_000));

        // 700k already + 400k attempted > 1M
        assertThatThrownBy(() -> useCase.execute(command(paid.id(), BigInteger.valueOf(400_000))))
            .isInstanceOf(InvoiceNotRefundableException.class)
            .hasMessageContaining("exceeds");

        verify(idempotency).releaseClaim(eq(KEY), any());  // claim is released on validation failure
        verifyNoInteractions(transfers, gateway);
    }

    @Test
    void releases_claim_when_custody_send_fails() {
        Invoice paid = paidInvoice(BigInteger.valueOf(1_000_000));
        when(idempotency.beginClaim(any(), any())).thenReturn(Optional.empty());
        when(invoices.findById(paid.id())).thenReturn(Optional.of(paid));
        when(refunds.sumIssuedNonFailed(paid.id())).thenReturn(BigInteger.ZERO);
        when(transfers.sendTransfer(any())).thenThrow(new RuntimeException("cus-server down"));

        assertThatThrownBy(() -> useCase.execute(command(paid.id(), BigInteger.valueOf(100_000))))
            .isInstanceOf(RuntimeException.class);

        verify(idempotency).releaseClaim(eq(KEY), any());
        verifyNoInteractions(gateway);
    }

    private IssueRefundCommand command(InvoiceId invoiceId, BigInteger amount) {
        return new IssueRefundCommand(KEY, invoiceId, amount,
            "0xFROM", "0xTO", Optional.empty(), RefundReason.REQUESTED_BY_CUSTOMER);
    }

    private Invoice paidInvoice(BigInteger expected) {
        return Invoice.newlyCreated(
            InvoiceId.newId(), MerchantId.of(UUID.randomUUID()),
            USDC_ETH, expected, 12,
            AccountId.of(UUID.randomUUID()), "0xADDR",
            FIXED_NOW.minusSeconds(600)
        ).markPaid("0xdeadbeef", expected, 12, FIXED_NOW.minusSeconds(300));
    }

    private IssuedRefund sampleIssued(Invoice invoice) {
        return IssuedRefund.fresh(
            com.cpayment.payment.domain.model.RefundId.newId(),
            invoice.id(),
            invoice.merchantId(),
            invoice.asset(),
            "0xFROM", "0xTO",
            BigInteger.valueOf(400_000),
            RefundReason.REQUESTED_BY_CUSTOMER,
            Optional.empty(),
            TransferId.of(UUID.randomUUID()),
            FIXED_NOW
        );
    }
}
