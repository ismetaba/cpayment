package com.cpayment.custody.infra.cusserver.event;

import com.cpayment.custody.domain.event.CustodyEvent;
import com.cpayment.custody.domain.event.FailureReason;
import com.cpayment.custody.infra.cusserver.event.dto.TransactionUpdatePayloadDTO;
import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferUpdateMapperTest {

    private TransferUpdateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TransferUpdateMapper(new AssetIdMapper());
    }

    @Test
    void broadcast_maps_to_TransferBroadcast() {
        UUID id = UUID.randomUUID();
        Optional<CustodyEvent> result = mapper.toCustodyEvent(payload(id, "BROADCAST",
            "0xabc", null, null, null, null, null));

        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(CustodyEvent.TransferBroadcast.class);
        CustodyEvent.TransferBroadcast e = (CustodyEvent.TransferBroadcast) result.get();
        assertThat(e.id().value()).isEqualTo(id);
        assertThat(e.txHash()).isEqualTo("0xabc");
    }

    @Test
    void confirmed_maps_to_TransferConfirmed_with_fee() {
        UUID id = UUID.randomUUID();
        Optional<CustodyEvent> result = mapper.toCustodyEvent(payload(id, "CONFIRMED",
            "0xabc", 12, BigInteger.valueOf(21000), "ETHEREUM", "ETH", null));

        assertThat(result).isPresent();
        CustodyEvent.TransferConfirmed e = (CustodyEvent.TransferConfirmed) result.get();
        assertThat(e.confirmations()).isEqualTo(12);
        assertThat(e.feeActual()).isEqualTo(BigInteger.valueOf(21000));
        assertThat(e.feeAsset().canonical()).isEqualTo("eth:mainnet:eth");
    }

    @Test
    void confirmed_without_fee_asset_falls_back_to_network_native_asset() {
        UUID id = UUID.randomUUID();
        CustodyEvent.TransferConfirmed e = (CustodyEvent.TransferConfirmed) mapper.toCustodyEvent(
            payload(id, "CONFIRMED", "0xabc", 6, BigInteger.valueOf(1000), "TRON", null, null))
            .orElseThrow();
        // fee network known, asset missing -> native asset of that network, NOT ETH
        assertThat(e.feeAsset().canonical()).isEqualTo("tron:mainnet:trx");
    }

    @Test
    void confirmed_without_any_fee_network_is_rejected_as_mapping_failure() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> mapper.toCustodyEvent(
            payload(id, "CONFIRMED", "0xabc", 6, BigInteger.valueOf(1000), null, null, null)))
            .isInstanceOf(CustodyAdapterException.class);
    }

    @Test
    void failed_maps_reason_using_keyword_classification() {
        UUID id = UUID.randomUUID();
        CustodyEvent.TransferFailed e = (CustodyEvent.TransferFailed) mapper.toCustodyEvent(
            payload(id, "FAILED", null, null, null, null, null,
                "insufficient gas for transfer"))
            .orElseThrow();
        assertThat(e.reason()).isEqualTo(FailureReason.INSUFFICIENT_GAS);
    }

    @Test
    void replaced_maps_when_replacement_id_present() {
        UUID id = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        CustodyEvent.TransferReplaced e = (CustodyEvent.TransferReplaced) mapper.toCustodyEvent(
            new TransactionUpdatePayloadDTO(id, "REPLACED", null, null, null, null, null,
                "fee bump", replacement, Instant.now()))
            .orElseThrow();
        assertThat(e.oldId().value()).isEqualTo(id);
        assertThat(e.newId().value()).isEqualTo(replacement);
    }

    @Test
    void unknown_type_returns_empty() {
        Optional<CustodyEvent> result = mapper.toCustodyEvent(payload(UUID.randomUUID(),
            "SOMETHING_WEIRD", null, null, null, null, null, null));
        assertThat(result).isEmpty();
    }

    private static TransactionUpdatePayloadDTO payload(UUID id, String type, String txHash,
                                                       Integer conf, BigInteger fee,
                                                       String feeNet, String feeAsset,
                                                       String reason) {
        return new TransactionUpdatePayloadDTO(id, type, txHash, conf, fee, feeNet, feeAsset,
            reason, null, Instant.now());
    }
}
