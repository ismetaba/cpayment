package com.cpayment.custody.domain.model;

import java.math.BigInteger;

public record Balance(AssetId asset, BigInteger available, BalanceStatus status) {

    public enum BalanceStatus { OK, STALE, UNAVAILABLE }
}
