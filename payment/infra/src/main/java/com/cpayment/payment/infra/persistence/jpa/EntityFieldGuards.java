package com.cpayment.payment.infra.persistence.jpa;

/**
 * Shared "schema invariant" helpers used by entity-to-domain mappers. When a row
 * read back from the database is missing a value that the matching domain variant
 * requires (e.g. {@code txHash} on a {@code BROADCAST} payout row), we want to fail
 * loudly with a message that names the entity + field — not a {@code NullPointerException}
 * deep in a record constructor.
 *
 * <p>These guards are deliberately type-blind: each mapper passes the entity's
 * id-and-status (so the diagnostic message is useful) plus the actual value. Keeping
 * the helpers in one place is the DRY pay-off from refactoring both
 * {@code PayoutMapper} and {@code RefundMapper} to use it.
 */
final class EntityFieldGuards {

    private EntityFieldGuards() {}

    static <T> T requireNonNull(T value, String field, Object entityRef, Object status) {
        if (value == null) throw missing(entityRef, status, field);
        return value;
    }

    static String requireNonBlank(String value, String field, Object entityRef, Object status) {
        if (value == null || value.isBlank()) throw missing(entityRef, status, field);
        return value;
    }

    private static IllegalStateException missing(Object entityRef, Object status, String field) {
        return new IllegalStateException(
            "entity " + entityRef + " status " + status + " is missing required field " + field);
    }
}
