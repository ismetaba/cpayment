package com.cpayment.custody.domain.model;

public record CreateWalletCommand(String label, WalletPurpose purpose) {}
