package com.cpayment.custody.domain.model;

/**
 * Opaque approval credential — typically a WebAuthn assertion or detached signature.
 * The actual byte structure is provider-specific; domain treats it as a sealed envelope.
 */
public record ApprovalProof(String scheme, byte[] payload) {}
