package com.cpayment.custody.domain.model;

/**
 * Discloses to the caller how a batch behaves on partial failure.
 *
 * <p>LSP-safe design: the caller asks the provider what semantics it offers and decides
 * whether to use sendBatch at all. cus-server (looped) returns BEST_EFFORT; Fireblocks (native batch)
 * may return ATOMIC.
 */
public enum BatchSemantic {
    /** Each item is sent independently; some may succeed and some may fail. */
    BEST_EFFORT,
    /** All-or-nothing; either every item succeeds or none does. */
    ATOMIC
}
