package com.cpayment.payment.infra.web;

import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutCreatedResult;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.usecase.ExecutePayoutUseCase;
import com.cpayment.payment.domain.usecase.FindPayoutUseCase;
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
public class PayoutController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final ExecutePayoutUseCase executePayout;
    private final FindPayoutUseCase findPayout;

    public PayoutController(ExecutePayoutUseCase executePayout, FindPayoutUseCase findPayout) {
        this.executePayout = executePayout;
        this.findPayout = findPayout;
    }

    @PostMapping
    public ResponseEntity<PayoutResponse> create(
            @RequestHeader(IDEMPOTENCY_HEADER) @NotBlank String idempotencyKey,
            @Valid @RequestBody CreatePayoutRequest request) {

        PayoutCreatedResult result = executePayout.execute(request.toCommand(idempotencyKey));
        PayoutResponse body = PayoutResponse.from(result.payout());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(body.id()).toUri();

        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
    }

    @GetMapping("/{id}")
    public PayoutResponse get(@PathVariable UUID id) {
        Payout payout = findPayout.byId(PayoutId.of(id));
        return PayoutResponse.from(payout);
    }
}
