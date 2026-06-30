package com.cpayment.payment.infra.web;

import com.cpayment.core.exception.IdempotencyConflictException;
import com.cpayment.core.exception.IdempotencyInProgressException;
import com.cpayment.custody.domain.exception.CustodyException;
import com.cpayment.payment.domain.exception.InvoiceNotFoundException;
import com.cpayment.payment.domain.exception.MerchantWalletNotConfiguredException;
import com.cpayment.payment.domain.exception.PaymentException;
import com.cpayment.payment.domain.exception.PayoutNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse(ex.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", ex.getMessage()));
    }

    /**
     * @PathVariable / @RequestParam type binding failure (e.g. malformed UUID).
     * Was falling through to the catch-all 500; now correctly returns 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Class<?> expected = ex.getRequiredType();
        String type = expected != null ? expected.getSimpleName() : "?";
        return ResponseEntity.badRequest().body(ApiError.of(
            "BAD_REQUEST",
            "parameter '" + ex.getName() + "' must be a valid " + type));
    }

    /**
     * A required {@code @RequestHeader} (e.g. the mandatory {@code Idempotency-Key})
     * is absent. A missing required header is a client error — was falling through
     * to the catch-all 500; now correctly returns 400.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> onMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(
            "BAD_REQUEST", "required header '" + ex.getHeaderName() + "' is missing"));
    }

    /**
     * {@code @Validated}-triggered violations on method parameters (e.g. a blank
     * {@code Idempotency-Key} failing {@code @NotBlank}). Also a 400, not a 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> onIdempotencyConflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("IDEMPOTENCY_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ApiError> onIdempotencyInProgress(IdempotencyInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("IDEMPOTENCY_IN_PROGRESS", ex.getMessage()));
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<ApiError> onInvoiceNotFound(InvoiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("INVOICE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(PayoutNotFoundException.class)
    public ResponseEntity<ApiError> onPayoutNotFound(PayoutNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("PAYOUT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MerchantWalletNotConfiguredException.class)
    public ResponseEntity<ApiError> onMerchantWallet(MerchantWalletNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiError.of("MERCHANT_WALLET_NOT_CONFIGURED", ex.getMessage()));
    }

    @ExceptionHandler(CustodyException.class)
    public ResponseEntity<ApiError> onCustody(CustodyException ex) {
        log.warn("custody error", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError.of("CUSTODY_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiError> onPayment(PaymentException ex) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of("PAYMENT_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "unexpected"));
    }
}
