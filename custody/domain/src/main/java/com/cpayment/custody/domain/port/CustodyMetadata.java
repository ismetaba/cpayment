package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.capability.Capabilities;
import com.cpayment.custody.domain.model.NetworkAsset;

import java.util.List;

public interface CustodyMetadata {
    Capabilities capabilities();
    List<NetworkAsset> supportedNetworkAssets();
    String providerName();
}
