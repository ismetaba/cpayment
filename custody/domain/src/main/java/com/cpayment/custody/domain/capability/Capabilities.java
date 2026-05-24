package com.cpayment.custody.domain.capability;

import com.cpayment.custody.domain.model.BatchSemantic;

import java.util.Set;

/**
 * Structured capability descriptor — orthogonal dimensions, not flat flags.
 * Callers ask the adapter "what kind of provider are you?" and adapt their flow.
 */
public record Capabilities(
    BatchSemantic batch,
    GasManagement gas,
    ApprovalModel approval,
    EventTransport eventTransport,
    Set<OptionalFeature> features
) {

    public boolean supports(OptionalFeature f) { return features.contains(f); }
}
