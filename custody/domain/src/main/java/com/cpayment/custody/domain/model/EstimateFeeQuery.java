package com.cpayment.custody.domain.model;

import java.math.BigInteger;

public record EstimateFeeQuery(AssetId asset, BigInteger amount, FeePreference preference) {}
