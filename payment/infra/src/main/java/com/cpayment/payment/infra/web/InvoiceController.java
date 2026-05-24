package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.InvoiceCreatedResult;
import com.cpayment.payment.domain.usecase.CreateInvoiceUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final CreateInvoiceUseCase createInvoice;

    public InvoiceController(CreateInvoiceUseCase createInvoice) {
        this.createInvoice = createInvoice;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(
            @RequestHeader(IDEMPOTENCY_HEADER) @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateInvoiceRequest request) {

        InvoiceCreatedResult result = createInvoice.execute(request.toCommand(idempotencyKey));
        InvoiceResponse body = InvoiceResponse.from(result.invoice());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(body.id()).toUri();

        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
    }
}
