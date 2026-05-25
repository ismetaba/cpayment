package com.cpayment.payment.infra.gas;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.Balance;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.TransferId;
import com.cpayment.custody.domain.port.BalancePort;
import com.cpayment.custody.domain.port.TransferPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GasFunderServiceTest {

    private static final NetworkId ETH = new NetworkId("eth", "mainnet");
    private static final AssetId   ETH_NATIVE = new AssetId(ETH, "eth");
    private static final String    TARGET = "0xWORKER";
    private static final BigInteger LOW   = new BigInteger("100000000000000000");   // 0.1 ETH
    private static final BigInteger TOPUP = new BigInteger("500000000000000000");   // 0.5 ETH
    private static final Instant FIXED_NOW = Instant.parse("2026-05-28T12:00:00Z");

    private BalancePort balancePort;
    private TransferPort transferPort;
    private GasFunderService service;
    private GasFunderProperties.Funder funder;

    @BeforeEach
    void setUp() {
        balancePort = mock(BalancePort.class);
        transferPort = mock(TransferPort.class);
        service = new GasFunderService(balancePort, transferPort,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        funder = new GasFunderProperties.Funder(
            ETH.canonical(), ETH_NATIVE.canonical(), "0xGASFUNDER",
            LOW, TOPUP, null);
    }

    @Test
    void skips_top_up_when_balance_is_above_low_water_mark() {
        when(balancePort.getBalanceByAddress(ETH, ETH_NATIVE, TARGET))
            .thenReturn(new Balance(ETH_NATIVE, LOW.multiply(BigInteger.TWO), Balance.BalanceStatus.OK));

        boolean fired = service.ensureGas(funder, TARGET);

        assertThat(fired).isFalse();
        verify(transferPort, never()).sendTransfer(any());
    }

    @Test
    void fires_top_up_when_balance_below_low_water_mark() {
        when(balancePort.getBalanceByAddress(ETH, ETH_NATIVE, TARGET))
            .thenReturn(new Balance(ETH_NATIVE, BigInteger.ZERO, Balance.BalanceStatus.OK));
        when(transferPort.sendTransfer(any())).thenReturn(TransferId.of(UUID.randomUUID()));

        boolean fired = service.ensureGas(funder, TARGET);

        assertThat(fired).isTrue();

        ArgumentCaptor<SendTransferCommand> cap = ArgumentCaptor.forClass(SendTransferCommand.class);
        verify(transferPort).sendTransfer(cap.capture());
        SendTransferCommand sent = cap.getValue();
        assertThat(sent.fromAddress()).isEqualTo("0xGASFUNDER");
        assertThat(sent.toAddress()).isEqualTo(TARGET);
        assertThat(sent.asset()).isEqualTo(ETH_NATIVE);
        assertThat(sent.amount()).isEqualTo(TOPUP);
        assertThat(sent.idempotencyKey().value()).startsWith("gas-" + TARGET + "-");
    }

    @Test
    void absorbs_balance_read_failure_and_does_NOT_top_up() {
        when(balancePort.getBalanceByAddress(any(), any(), any()))
            .thenThrow(new RuntimeException("cus-server down"));

        boolean fired = service.ensureGas(funder, TARGET);

        assertThat(fired).isFalse();
        verify(transferPort, never()).sendTransfer(any());
    }

    @Test
    void absorbs_transfer_failure_and_returns_false() {
        when(balancePort.getBalanceByAddress(any(), any(), any()))
            .thenReturn(new Balance(ETH_NATIVE, BigInteger.ZERO, Balance.BalanceStatus.OK));
        when(transferPort.sendTransfer(any())).thenThrow(new RuntimeException("send failed"));

        boolean fired = service.ensureGas(funder, TARGET);

        assertThat(fired).isFalse();
    }

    @Test
    void rejects_funder_with_mismatched_network_and_native_asset() {
        GasFunderProperties.Funder bad = new GasFunderProperties.Funder(
            "eth:mainnet", "tron:mainnet:trx", "0xGAS", LOW, TOPUP, null);

        boolean fired = service.ensureGas(bad, TARGET);

        assertThat(fired).isFalse();
        verify(balancePort, never()).getBalanceByAddress(any(), any(), any());
    }
}
