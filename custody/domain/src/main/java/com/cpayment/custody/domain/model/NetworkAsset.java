package com.cpayment.custody.domain.model;

public record NetworkAsset(NetworkId network, String symbol, int decimals, boolean active) {}
