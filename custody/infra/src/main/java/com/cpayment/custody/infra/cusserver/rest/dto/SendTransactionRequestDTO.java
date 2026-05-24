package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigInteger;

/**
 * Mirror of cus-server's {@code SendTransactionRequestDTO}. {@code feeStrategy} is sent
 * as the polymorphic token produced by {@link com.cpayment.custody.infra.cusserver.mapping.FeeStrategyMapper}.
 *
 * <p>NOTE: cus-server's actual DTO uses a polymorphic {@code FeeStrategy} class hierarchy.
 * For first cut we serialize a string token; production should switch to a sealed JSON
 * polymorphic representation matching cus-server's schema once it's stable enough to depend on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendTransactionRequestDTO(
    String fromAddress,
    String toAddress,
    BigInteger amount,
    String networkName,
    String assetName,
    String feeStrategy,
    Object networkSpecificParams
) {}
