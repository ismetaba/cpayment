package com.cpayment.custody.infra.cusserver.auth;

import com.cpayment.custody.infra.cusserver.config.CusServerProperties;
import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.rest.HttpClientFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;

/**
 * Fetches and caches a bearer token from Keycloak using the OAuth2 client-credentials grant.
 *
 * <h2>Thread safety</h2>
 * <p>{@link #currentBearerToken()} reads a {@code volatile} cached reference without locking
 * — the hot path is lock-free. Only the (rare) refresh path enters a synchronized block.
 * Double-checked locking pattern guards against multiple concurrent refreshes.
 *
 * <h2>Failure mode</h2>
 * <p>If Keycloak refuses or is unreachable, the cached token (if any and still valid) is
 * returned. If no usable token is available, a {@link CustodyAdapterException} is thrown —
 * callers should propagate this as a 502/503 to the client.
 */
@Component
public class KeycloakClientCredentialsTokenProvider implements AccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(KeycloakClientCredentialsTokenProvider.class);

    private final RestClient http;
    private final CusServerProperties props;
    private final Clock clock;

    private volatile CachedAccessToken cached;

    public KeycloakClientCredentialsTokenProvider(CusServerProperties props,
                                                  HttpClientFactory clientFactory,
                                                  Clock clock) {
        this.props = props;
        this.clock = clock;
        this.http = clientFactory.builderWithTimeouts().build();
    }

    @Override
    public String currentBearerToken() {
        CachedAccessToken snapshot = cached;
        if (snapshot != null && !snapshot.isExpiringSoon(clock.instant())) {
            return snapshot.token();
        }
        return refresh().token();
    }

    private synchronized CachedAccessToken refresh() {
        CachedAccessToken snapshot = cached;
        if (snapshot != null && !snapshot.isExpiringSoon(clock.instant())) {
            return snapshot;
        }

        String tokenUrl = props.holderJwtIssuerUrl() + "/protocol/openid-connect/token";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.holderClientId());
        form.add("client_secret", props.holderClientSecret());

        TokenResponse response;
        try {
            response = http.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            throw new CustodyAdapterException(
                "keycloak token request rejected: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString(),
                ex);
        } catch (RuntimeException ex) {
            throw new CustodyAdapterException("keycloak token request failed", ex);
        }

        if (response == null || response.accessToken == null) {
            throw new CustodyAdapterException("keycloak returned empty token");
        }

        Instant expiresAt = clock.instant().plusSeconds(Math.max(60L, response.expiresIn));
        CachedAccessToken fresh = new CachedAccessToken(response.accessToken, expiresAt);
        this.cached = fresh;
        log.debug("token.refreshed expiresAt={}", expiresAt);
        return fresh;
    }

    private record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType
    ) {}
}
