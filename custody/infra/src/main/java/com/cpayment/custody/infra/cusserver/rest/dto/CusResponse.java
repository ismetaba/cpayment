package com.cpayment.custody.infra.cusserver.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * cus-server wraps every endpoint response in a {@code Response<T>} envelope.
 * We mirror the shape conservatively — only the {@code data} field is required;
 * everything else is captured-but-ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CusResponse<T>(T data, String message, String errorCode) {
}
