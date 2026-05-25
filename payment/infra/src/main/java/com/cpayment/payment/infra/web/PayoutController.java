package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutCreatedResult;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.usecase.CancelPayoutUseCase;
import com.cpayment.payment.domain.usecase.ExecutePayoutUseCase;
import com.cpayment.payment.domain.usecase.FindPayoutUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/payouts")
@Tag(name = "Payouts", description = "Merchant-initiated outbound transfers.")
public class PayoutController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final ExecutePayoutUseCase executePayout;
    private final FindPayoutUseCase findPayout;
    private final CancelPayoutUseCase cancelPayout;

    public PayoutController(ExecutePayoutUseCase executePayout,
                            FindPayoutUseCase findPayout,
                            CancelPayoutUseCase cancelPayout) {
        this.executePayout = executePayout;
        this.findPayout = findPayout;
        this.cancelPayout = cancelPayout;
    }

    @PostMapping
    @Operation(
        summary = "Submit a payout",
        description = "Sends a custody transfer from the merchant's address to the supplied destination. "
            + "The Idempotency-Key header is REQUIRED. The response status is SUBMITTED — the on-chain "
            + "status (BROADCAST → CONFIRMED) advances asynchronously and is delivered via merchant "
            + "webhooks."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payout submitted to custody. Location header points to GET /payouts/{id}."),
        @ApiResponse(responseCode = "400", description = "Validation failed."),
        @ApiResponse(responseCode = "409", description = "Idempotency conflict or in-flight."),
        @ApiResponse(responseCode = "502", description = "Custody backend error.")
    })
    public ResponseEntity<PayoutResponse> create(
            @Parameter(name = "Idempotency-Key", required = true, in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER)
            @RequestHeader(IDEMPOTENCY_HEADER) @NotBlank String idempotencyKey,
            @Valid @RequestBody CreatePayoutRequest request) {

        PayoutCreatedResult result = executePayout.execute(request.toCommand(idempotencyKey));
        PayoutResponse body = PayoutResponse.from(result.payout());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(body.id()).toUri();

        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a payout by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payout found."),
        @ApiResponse(responseCode = "404", description = "Payout not found.")
    })
    public PayoutResponse get(@PathVariable UUID id) {
        Payout payout = findPayout.byId(PayoutId.of(id));
        return PayoutResponse.from(payout);
    }

    @PostMapping("/{id}/cancel")
    @Operation(
        summary = "Cancel a payout",
        description = "Only valid before broadcast (REQUESTED or SUBMITTED). cpayment marks the local "
            + "aggregate CANCELLED and emits PAYOUT_CANCELLED webhook; if cus-server has already started "
            + "broadcast, the subsequent Transfer* events will reconcile the actual on-chain outcome.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payout cancelled (or was already cancelled)."),
        @ApiResponse(responseCode = "404", description = "Payout not found."),
        @ApiResponse(responseCode = "422", description = "Payout is in a non-cancellable status.")
    })
    public PayoutResponse cancel(@PathVariable UUID id) {
        Payout cancelled = cancelPayout.execute(PayoutId.of(id));
        return PayoutResponse.from(cancelled);
    }
}
