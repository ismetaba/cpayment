package com.cpayment.payment.domain.usecase;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.model.IdempotencyKey;
import com.cpayment.custody.domain.model.Account;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.CreateAccountCommand;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.custody.domain.port.AccountPort;
import com.cpayment.payment.domain.model.CreateInvoiceCommand;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceCreatedResult;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.InvoiceStatus;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.MerchantWalletResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateInvoiceUseCaseTest {

    private static final MerchantId MERCHANT = MerchantId.of(UUID.randomUUID());
    private static final WalletId WALLET = WalletId.of(UUID.randomUUID());
    private static final AssetId USDC_ETH = new AssetId(new NetworkId("eth", "mainnet"), "usdc");
    private static final IdempotencyKey KEY = IdempotencyKey.of("merchant-order-42");
    private static final Instant FIXED_NOW = Instant.parse("2026-05-24T12:00:00Z");

    private InvoiceRepository invoices;
    private InvoiceIdempotencyStore idempotency;
    private MerchantWalletResolver walletResolver;
    private AccountPort accounts;
    private CreateInvoiceUseCase useCase;

    @BeforeEach
    void setUp() {
        invoices = mock(InvoiceRepository.class);
        idempotency = mock(InvoiceIdempotencyStore.class);
        walletResolver = mock(MerchantWalletResolver.class);
        accounts = mock(AccountPort.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        useCase = new CreateInvoiceUseCase(invoices, idempotency, walletResolver, accounts, clock);
    }

    @Test
    void creates_invoice_with_deposit_address_returned_from_custody() {
        when(idempotency.findExisting(any(), any())).thenReturn(Optional.empty());
        when(walletResolver.resolveDepositWallet(MERCHANT, USDC_ETH.network())).thenReturn(WALLET);

        AccountId expectedAccountId = AccountId.of(UUID.randomUUID());
        when(accounts.createAccount(any(CreateAccountCommand.class))).thenReturn(new Account(
            expectedAccountId,
            Optional.of(WALLET),
            USDC_ETH.network(),
            "0xCAFEBABE000000000000000000000000DEADBEEF",
            "invoice-x",
            Set.of("USDC")
        ));

        InvoiceCreatedResult result = useCase.execute(command(BigInteger.valueOf(1_000_000)));

        Invoice invoice = result.invoice();
        assertThat(invoice.merchantId()).isEqualTo(MERCHANT);
        assertThat(invoice.asset()).isEqualTo(USDC_ETH);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.AWAITING_DEPOSIT);
        assertThat(invoice.custodyAccount()).isEqualTo(expectedAccountId);
        assertThat(invoice.depositAddress()).isEqualTo("0xCAFEBABE000000000000000000000000DEADBEEF");
        assertThat(invoice.createdAt()).isEqualTo(FIXED_NOW);

        ArgumentCaptor<CreateAccountCommand> cmdCaptor = ArgumentCaptor.forClass(CreateAccountCommand.class);
        verify(accounts).createAccount(cmdCaptor.capture());
        CreateAccountCommand sent = cmdCaptor.getValue();
        assertThat(sent.walletId()).isEqualTo(WALLET);
        assertThat(sent.supportedAssetSymbols()).containsExactly("usdc");

        verify(invoices).save(invoice);
        verify(idempotency).record(eqKey(KEY), any(String.class), eqInvoice(invoice));
    }

    @Test
    void idempotent_hit_returns_cached_invoice_without_side_effects() {
        Invoice cached = sampleInvoice();
        when(idempotency.findExisting(eqKey(KEY), any(String.class))).thenReturn(Optional.of(cached));

        InvoiceCreatedResult result = useCase.execute(command(BigInteger.valueOf(1_000_000)));

        assertThat(result.invoice()).isEqualTo(cached);
        verifyNoInteractions(walletResolver, accounts);
        verify(invoices, never()).save(any());
    }

    @Test
    void conflict_thrown_by_store_propagates_unchanged() {
        when(idempotency.findExisting(eqKey(KEY), any(String.class)))
            .thenThrow(new IdempotencyConflictException("different body for " + KEY.value()));

        assertThatThrownBy(() -> useCase.execute(command(BigInteger.valueOf(1_000_000))))
            .isInstanceOf(IdempotencyConflictException.class);

        verifyNoInteractions(walletResolver, accounts);
        verify(invoices, never()).save(any());
    }

    @Test
    void invoice_not_saved_when_custody_call_fails() {
        when(idempotency.findExisting(any(), any())).thenReturn(Optional.empty());
        when(walletResolver.resolveDepositWallet(any(), any())).thenReturn(WALLET);
        when(accounts.createAccount(any())).thenThrow(new RuntimeException("cus-server down"));

        assertThatThrownBy(() -> useCase.execute(command(BigInteger.valueOf(1_000_000))))
            .isInstanceOf(RuntimeException.class);

        verify(invoices, never()).save(any());
        verify(idempotency, never()).record(any(), any(), any());
    }

    private CreateInvoiceCommand command(BigInteger amount) {
        return new CreateInvoiceCommand(KEY, MERCHANT, USDC_ETH, amount);
    }

    private Invoice sampleInvoice() {
        return Invoice.newlyCreated(
            InvoiceId.newId(), MERCHANT, USDC_ETH, BigInteger.valueOf(1_000_000),
            AccountId.of(UUID.randomUUID()), "0xCACHED", FIXED_NOW);
    }

    private static IdempotencyKey eqKey(IdempotencyKey k) {
        return org.mockito.ArgumentMatchers.eq(k);
    }

    private static Invoice eqInvoice(Invoice i) {
        return org.mockito.ArgumentMatchers.eq(i);
    }
}
