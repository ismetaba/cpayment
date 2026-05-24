package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceCreatedResult;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.usecase.CreateInvoiceUseCase;
import com.cpayment.payment.domain.usecase.FindInvoiceUseCase;
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
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Deposit-address-backed invoices.")
public class InvoiceController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final CreateInvoiceUseCase createInvoice;
    private final FindInvoiceUseCase findInvoice;

    public InvoiceController(CreateInvoiceUseCase createInvoice, FindInvoiceUseCase findInvoice) {
        this.createInvoice = createInvoice;
        this.findInvoice = findInvoice;
    }

    @PostMapping
    @Operation(
        summary = "Create an invoice",
        description = "Provisions a custody deposit account for the merchant and returns the on-chain "
            + "address to send funds to. The Idempotency-Key header is REQUIRED — replays with the same "
            + "key + body return the original invoice without re-calling custody."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Invoice created. Location header points to GET /invoices/{id}."),
        @ApiResponse(responseCode = "400", description = "Validation failed."),
        @ApiResponse(responseCode = "409", description = "Idempotency key reused with a different body, or a prior attempt is still in-flight."),
        @ApiResponse(responseCode = "422", description = "Merchant wallet not configured for the requested network."),
        @ApiResponse(responseCode = "502", description = "Custody backend error.")
    })
    public ResponseEntity<InvoiceResponse> create(
            @Parameter(name = "Idempotency-Key", required = true, in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER)
            @RequestHeader(IDEMPOTENCY_HEADER) @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateInvoiceRequest request) {

        InvoiceCreatedResult result = createInvoice.execute(request.toCommand(idempotencyKey));
        InvoiceResponse body = InvoiceResponse.from(result.invoice());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(body.id()).toUri();

        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch an invoice by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invoice found."),
        @ApiResponse(responseCode = "404", description = "Invoice not found.")
    })
    public InvoiceResponse get(@PathVariable UUID id) {
        Invoice invoice = findInvoice.byId(InvoiceId.of(id));
        return InvoiceResponse.from(invoice);
    }
}
