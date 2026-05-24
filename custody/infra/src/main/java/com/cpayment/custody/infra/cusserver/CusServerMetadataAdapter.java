package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.capability.ApprovalModel;
import com.cpayment.custody.domain.capability.Capabilities;
import com.cpayment.custody.domain.capability.EventTransport;
import com.cpayment.custody.domain.capability.GasManagement;
import com.cpayment.custody.domain.capability.OptionalFeature;
import com.cpayment.custody.domain.model.BatchSemantic;
import com.cpayment.custody.domain.model.NetworkAsset;
import com.cpayment.custody.domain.port.CustodyMetadata;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;

@Component
public class CusServerMetadataAdapter implements CustodyMetadata {

    @Override
    public String providerName() { return "cus-server"; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(
            BatchSemantic.BEST_EFFORT,             // adapter loops single send-transfer calls
            GasManagement.CPAYMENT_OWNED,          // cpayment owns a GAS_FUNDER wallet
            ApprovalModel.AUTO,                    // policy currently auto-approves all
            EventTransport.BRIDGE,                 // RabbitMQ → custody-bridge → CustodyEventPublisher
            EnumSet.of(
                OptionalFeature.LOCAL_IDEMPOTENCY,
                OptionalFeature.ADDRESS_VALIDATION_LOCAL,
                OptionalFeature.RBF              // /holder/transactions/resend
                // MEMO, SCREENING, CANCEL — not yet supported
            )
        );
    }

    @Override
    public List<NetworkAsset> supportedNetworkAssets() {
        // TODO call GET /api/v1/public/network-assets and map via AssetIdMapper
        return List.of();
    }
}
