package com.cpayment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;

/**
 * Minimal HTTP security:
 *
 * <ul>
 *   <li>Merchant API ({@code /api/v1/**}), OpenAPI UI, JSON spec, and the
 *       k8s-probe-friendly Actuator endpoints (health, info) are open. Auth
 *       belongs to the API gateway upstream; the merchant API will gain JWT
 *       once that piece lands.</li>
 *   <li>All OTHER Actuator endpoints (metrics, prometheus, …) require HTTP
 *       Basic auth with credentials sourced from
 *       {@code cpayment.security.actuator.{username,password}} — set them per
 *       environment, never in the public repo.</li>
 *   <li>Everything else requires authentication (a 403 black hole for
 *       unknown paths is safer than the default permitAll).</li>
 * </ul>
 *
 * <p>CSRF is disabled because the API is consumed by server-to-server clients
 * (webhooks, gateways) with bearer/HMAC auth — never browser sessions.
 */
@Configuration
public class SecurityConfiguration {

    private static final String DEFAULT_ACTUATOR_PASSWORD = "change-me";
    /** Profiles where the built-in default actuator password is tolerated. */
    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("dev", "local", "test", "it");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                .requestMatchers("/api/v1/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers(EndpointRequest.toAnyEndpoint()).authenticated()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .build();
    }

    @Bean
    public UserDetailsService actuatorUser(
            @Value("${cpayment.security.actuator.username:cpayment-ops}") String username,
            @Value("${cpayment.security.actuator.password:change-me}")    String password,
            Environment env) {
        if (DEFAULT_ACTUATOR_PASSWORD.equals(password) && !isNonProductionProfile(env)) {
            throw new IllegalStateException(
                "cpayment.security.actuator.password is still the default '"
                    + DEFAULT_ACTUATOR_PASSWORD + "'. Set a real password (e.g. env "
                    + "CPAYMENT_SECURITY_ACTUATOR_PASSWORD) before starting outside a "
                    + "dev/test profile.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
            .password(password)
            .roles("ACTUATOR")
            .build());
    }

    private static boolean isNonProductionProfile(Environment env) {
        for (String profile : env.getActiveProfiles()) {
            if (NON_PRODUCTION_PROFILES.contains(profile)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Production should rotate to bcrypt + secret-management. For first cut the
        // password is read from env vars and never persisted, so NoOpPasswordEncoder
        // (with the {noop} prefix dropped by InMemoryUserDetailsManager.User) is
        // intentional and clearly documented here.
        return NoOpPasswordEncoder.getInstance();
    }
}
