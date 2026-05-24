package com.cpayment.custody.infra.cusserver.auth;

/**
 * Hands out a current, non-expired bearer token for outbound cus-server calls.
 * Implementations are responsible for caching and refresh behavior; callers
 * should treat the returned string as ephemeral.
 */
public interface AccessTokenProvider {

    String currentBearerToken();
}
