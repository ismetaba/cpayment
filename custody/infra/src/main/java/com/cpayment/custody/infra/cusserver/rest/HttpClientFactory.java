package com.cpayment.custody.infra.cusserver.rest;

import com.cpayment.custody.infra.cusserver.config.CusServerProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Single source of truth for outbound HTTP timeouts. Every {@link RestClient} that talks
 * to cus-server or its identity provider goes through this factory so we can never
 * forget to bound a remote call.
 */
@Component
public class HttpClientFactory {

    private final CusServerProperties props;

    public HttpClientFactory(CusServerProperties props) {
        this.props = props;
    }

    public RestClient.Builder builderWithTimeouts() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) props.timeouts().connect().toMillis());
        rf.setReadTimeout((int) props.timeouts().read().toMillis());
        return RestClient.builder().requestFactory(rf);
    }
}
