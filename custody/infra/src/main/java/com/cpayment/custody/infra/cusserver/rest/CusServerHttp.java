package com.cpayment.custody.infra.cusserver.rest;

import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.rest.dto.CusResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared cus-server HTTP plumbing: issue a POST and unwrap the {@link CusResponse}
 * envelope, translating transport and HTTP error responses into
 * {@link CustodyAdapterException}. Extracted so the per-port adapters don't each
 * re-implement identical request / response / error handling.
 *
 * <p>Retry and circuit-breaker policy is orthogonal and stays the caller's choice —
 * wrap the {@link #post} call in {@link ResilientHttpExecutor#get} (idempotent reads)
 * or {@link ResilientHttpExecutor#write} (non-idempotent writes).
 */
@Component
public class CusServerHttp {

    private final CusServerRestClient client;

    public CusServerHttp(CusServerRestClient client) {
        this.client = client;
    }

    /** POST {@code body} to {@code path}, wrapping HTTP/transport failures. */
    public <T> CusResponse<T> post(String path, Object body,
                                   ParameterizedTypeReference<CusResponse<T>> ref) {
        try {
            return client.http().post().uri(path).body(body).retrieve().body(ref);
        } catch (RestClientResponseException ex) {
            throw new CustodyAdapterException(
                "cus-server POST " + path + " failed: " + ex.getStatusCode() + " "
                    + ex.getResponseBodyAsString(), ex);
        } catch (RuntimeException ex) {
            throw new CustodyAdapterException("cus-server POST " + path + " failed", ex);
        }
    }

    /** Unwrap the {@code data} envelope, or fail if cus-server returned none. */
    public <T> T requireData(CusResponse<T> response, String operation) {
        if (response == null || response.data() == null) {
            throw new CustodyAdapterException(operation + ": cus-server returned empty data");
        }
        return response.data();
    }
}
