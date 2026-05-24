package com.cpayment.custody.domain.model;

import java.math.BigInteger;

public record FeeQuote(AssetId feeAsset, BigInteger estimatedFee, FeePreference matchedPreference) {}
