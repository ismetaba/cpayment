package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.InvoiceStatus;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.NoOpPaymentMetrics;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordDepositUseCaseTest {

    private static final AssetId USDC_ETH = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
    private static final Instant FIXED_NOW = Instant.parse("2026-05-24T12:00:00Z");

    private InvoiceRepository invoices;
    private RecordDepositUseCase useCase;

    @BeforeEach
    void setUp() {
        invoices = mock(InvoiceRepository.class);
        useCase = new RecordDepositUseCase(
            invoices, NoOpPaymentMetrics.INSTANCE, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void marks_invoice_paid_when_amount_meets_expected() {
        Invoice invoice = awaitingDeposit(BigInteger.valueOf(1_000_000));
        when(invoices.findByCustodyAccount(invoice.custodyAccount())).thenReturn(Optional.of(invoice));

        useCase.handle(deposit(invoice, BigInteger.valueOf(1_000_000)));

        ArgumentCaptor<Invoice> savedCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices).save(savedCaptor.capture());
        Invoice saved = savedCaptor.getValue();
        assertThat(saved.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(saved.receivedAmount()).contains(BigInteger.valueOf(1_000_000));
        assertThat(saved.receivedTxHash()).contains("0xdeadbeef");
        assertThat(saved.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void marks_invoice_underpaid_when_amount_is_short() {
        Invoice invoice = awaitingDeposit(BigInteger.valueOf(1_000_000));
        when(invoices.findByCustodyAccount(invoice.custodyAccount())).thenReturn(Optional.of(invoice));

        useCase.handle(deposit(invoice, BigInteger.valueOf(999_999)));

        ArgumentCaptor<Invoice> savedCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().status()).isEqualTo(InvoiceStatus.UNDERPAID);
    }

    @Test
    void drops_orphan_deposit_without_saving() {
        when(invoices.findByCustodyAccount(any(AccountId.class))).thenReturn(Optional.empty());

        useCase.handle(new CustodyEvent.DepositDetected(
            AccountId.of(UUID.randomUUID()), "0xFROM", USDC_ETH,
            BigInteger.TEN, "0xdeadbeef", 1));

        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    void ignores_redelivered_event_when_invoice_already_paid() {
        Invoice paid = awaitingDeposit(BigInteger.TEN)
            .markPaid("0xprev", BigInteger.TEN, FIXED_NOW.minusSeconds(60));
        when(invoices.findByCustodyAccount(paid.custodyAccount())).thenReturn(Optional.of(paid));

        useCase.handle(deposit(paid, BigInteger.TEN));

        verify(invoices, never()).save(any(Invoice.class));
    }

    private static Invoice awaitingDeposit(BigInteger expected) {
        return Invoice.newlyCreated(
            InvoiceId.newId(),
            MerchantId.of(UUID.randomUUID()),
            USDC_ETH,
            expected,
            AccountId.of(UUID.randomUUID()),
            "0xADDRESS",
            FIXED_NOW.minusSeconds(120)
        );
    }

    private static CustodyEvent.DepositDetected deposit(Invoice invoice, BigInteger amount) {
        return new CustodyEvent.DepositDetected(
            invoice.custodyAccount(), "0xFROM", invoice.asset(), amount, "0xdeadbeef", 1);
    }
}
