package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.Refund;
import com.cpayment.payment.domain.model.RefundCreatedResult;
import com.cpayment.payment.domain.model.RefundId;
import com.cpayment.payment.domain.usecase.FindRefundUseCase;
import com.cpayment.payment.domain.usecase.IssueRefundUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
@RequestMapping("/api/v1")
@Tag(name = "Refunds", description = "Outbound refunds against a previously-PAID invoice.")
public class RefundController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IssueRefundUseCase issueRefund;
    private final FindRefundUseCase findRefund;

    public RefundController(IssueRefundUseCase issueRefund, FindRefundUseCase findRefund) {
        this.issueRefund = issueRefund;
        this.findRefund = findRefund;
    }

    @PostMapping("/invoices/{invoiceId}/refunds")
    @Operation(
        summary = "Issue a refund against an invoice",
        description = "Refunds are partial-allowed: cpayment enforces "
            + "sum(refunds.amount) <= invoice.expectedAmount. The Idempotency-Key header is "
            + "REQUIRED. The response status is ISSUED; on-chain progression (BROADCAST → "
            + "CONFIRMED) is delivered via merchant webhooks."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Refund issued. Location header points to GET /refunds/{id}."),
        @ApiResponse(responseCode = "400", description = "Validation failed."),
        @ApiResponse(responseCode = "404", description = "Invoice not found."),
        @ApiResponse(responseCode = "409", description = "Idempotency conflict or in-flight."),
        @ApiResponse(responseCode = "422", description = "Invoice not in PAID state or refund exceeds available."),
        @ApiResponse(responseCode = "502", description = "Custody backend error.")
    })
    public ResponseEntity<RefundResponse> issue(
            @PathVariable UUID invoiceId,
            @Parameter(name = "Idempotency-Key", required = true, in = ParameterIn.HEADER)
            @RequestHeader(IDEMPOTENCY_HEADER) @NotBlank String idempotencyKey,
            @Valid @RequestBody IssueRefundRequest request) {

        RefundCreatedResult result = issueRefund.execute(request.toCommand(invoiceId, idempotencyKey));
        RefundResponse body = RefundResponse.from(result.refund());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/refunds/{id}").buildAndExpand(body.id()).toUri();

        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
    }

    @GetMapping("/refunds/{id}")
    @Operation(summary = "Fetch a refund by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Refund found."),
        @ApiResponse(responseCode = "404", description = "Refund not found.")
    })
    public RefundResponse get(@PathVariable UUID id) {
        Refund refund = findRefund.byId(RefundId.of(id));
        return RefundResponse.from(refund);
    }
}
