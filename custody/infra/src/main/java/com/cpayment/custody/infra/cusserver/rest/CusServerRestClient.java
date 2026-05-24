package com.cpayment.custody.infra.cusserver.rest;

import com.cpayment.custody.infra.cusserver.auth.BearerTokenInterceptor;
import com.cpayment.custody.infra.cusserver.config.CusServerProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Single source of truth for HTTP calls against cus-server. The {@link BearerTokenInterceptor}
 * attaches a fresh OAuth2 access token on every request; the {@link HttpClientFactory}
 * guarantees connect/read timeouts.
 */
@Component
public class CusServerRestClient {

    private final RestClient http;
    private final CusServerProperties props;

    public CusServerRestClient(CusServerProperties props,
                               HttpClientFactory clientFactory,
                               BearerTokenInterceptor authInterceptor) {
        this.props = props;
        this.http = clientFactory.builderWithTimeouts()
            .baseUrl(props.baseUrl())
            .requestInterceptor(authInterceptor)
            .build();
    }

    public RestClient http() { return http; }
    public CusServerProperties props() { return props; }
}
