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
    private static final Instant FIXED_NOW = Instant.parse("2026-05-25T12:00:00Z");
    private static final int MIN_CONFIRMATIONS = 12;

    private InvoiceRepository invoices;
    private RecordDepositUseCase useCase;

    @BeforeEach
    void setUp() {
        invoices = mock(InvoiceRepository.class);
        useCase = new RecordDepositUseCase(
            invoices, NoOpPaymentMetrics.INSTANCE, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void detected_with_low_confirmations_moves_invoice_to_DETECTED() {
        Invoice invoice = awaitingDeposit(BigInteger.valueOf(1_000_000));
        when(invoices.findByCustodyAccount(invoice.custodyAccount())).thenReturn(Optional.of(invoice));

        useCase.onDetected(detected(invoice, BigInteger.valueOf(1_000_000), 3));

        Invoice saved = captureSaved();
        assertThat(saved.status()).isEqualTo(InvoiceStatus.DETECTED);
        assertThat(saved.receivedConfirmations()).contains(3);
    }

    @Test
    void detected_with_sufficient_confirmations_jumps_to_PAID() {
        Invoice invoice = awaitingDeposit(BigInteger.valueOf(1_000_000));
        when(invoices.findByCustodyAccount(invoice.custodyAccount())).thenReturn(Optional.of(invoice));

        useCase.onDetected(detected(invoice, BigInteger.valueOf(1_000_000), MIN_CONFIRMATIONS));

        assertThat(captureSaved().status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void confirmed_event_marks_invoice_PAID_even_when_previously_DETECTED() {
        Invoice detected = awaitingDeposit(BigInteger.valueOf(1_000_000))
            .markDetected("0xprev", BigInteger.valueOf(1_000_000), 3, FIXED_NOW.minusSeconds(60));
        when(invoices.findByCustodyAccount(detected.custodyAccount())).thenReturn(Optional.of(detected));

        useCase.onConfirmed(confirmed(detected, BigInteger.valueOf(1_000_000), MIN_CONFIRMATIONS));

        assertThat(captureSaved().status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void underpayment_short_circuits_to_UNDERPAID_regardless_of_confirmations() {
        Invoice invoice = awaitingDeposit(BigInteger.valueOf(1_000_000));
        when(invoices.findByCustodyAccount(invoice.custodyAccount())).thenReturn(Optional.of(invoice));

        useCase.onDetected(detected(invoice, BigInteger.valueOf(999_999), MIN_CONFIRMATIONS));

        assertThat(captureSaved().status()).isEqualTo(InvoiceStatus.UNDERPAID);
    }

    @Test
    void orphan_deposit_is_dropped() {
        when(invoices.findByCustodyAccount(any(AccountId.class))).thenReturn(Optional.empty());

        useCase.onDetected(new CustodyEvent.DepositDetected(
            AccountId.of(UUID.randomUUID()), "0xFROM", USDC_ETH,
            BigInteger.TEN, "0xdead", 1));

        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    void terminal_invoice_ignores_redelivered_event() {
        Invoice paid = awaitingDeposit(BigInteger.TEN)
            .markPaid("0xprev", BigInteger.TEN, MIN_CONFIRMATIONS, FIXED_NOW.minusSeconds(60));
        when(invoices.findByCustodyAccount(paid.custodyAccount())).thenReturn(Optional.of(paid));

        useCase.onDetected(detected(paid, BigInteger.TEN, MIN_CONFIRMATIONS));

        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    void duplicate_DETECTED_at_same_depth_does_not_resave() {
        Invoice already = awaitingDeposit(BigInteger.valueOf(1_000_000))
            .markDetected("0xtx", BigInteger.valueOf(1_000_000), 3, FIXED_NOW.minusSeconds(60));
        when(invoices.findByCustodyAccount(already.custodyAccount())).thenReturn(Optional.of(already));

        useCase.onDetected(detected(already, BigInteger.valueOf(1_000_000), 3));

        verify(invoices, never()).save(any(Invoice.class));
    }

    private Invoice captureSaved() {
        ArgumentCaptor<Invoice> c = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices).save(c.capture());
        return c.getValue();
    }

    private static Invoice awaitingDeposit(BigInteger expected) {
        return Invoice.newlyCreated(
            InvoiceId.newId(),
            MerchantId.of(UUID.randomUUID()),
            USDC_ETH,
            expected,
            MIN_CONFIRMATIONS,
            AccountId.of(UUID.randomUUID()),
            "0xADDRESS",
            FIXED_NOW.minusSeconds(120)
        );
    }

    private static CustodyEvent.DepositDetected detected(Invoice invoice, BigInteger amount, int conf) {
        return new CustodyEvent.DepositDetected(
            invoice.custodyAccount(), "0xFROM", invoice.asset(), amount, "0xdead", conf);
    }

    private static CustodyEvent.DepositConfirmed confirmed(Invoice invoice, BigInteger amount, int conf) {
        return new CustodyEvent.DepositConfirmed(
            invoice.custodyAccount(), "0xFROM", invoice.asset(), amount, "0xdead", conf);
    }
}
