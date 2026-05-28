package com.cpayment.custody.infra.cusserver.rest;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientHttpExecutorTest {

    private ResilientHttpExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ResilientHttpExecutor(new SimpleMeterRegistry());
        executor.bindMetrics();
    }

    @Test
    void get_retries_on_server_error_and_returns_when_one_succeeds() {
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.get(() -> {
            int n = attempts.incrementAndGet();
            if (n < 3) throw HttpServerErrorException.create(
                HttpStatusCode.valueOf(503), "down", null, null, null);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void get_does_not_retry_on_client_error() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executor.get(() -> {
            attempts.incrementAndGet();
            throw HttpClientErrorException.create(
                HttpStatusCode.valueOf(400), "bad", null, null, null);
        })).isInstanceOf(HttpClientErrorException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void write_never_retries_even_on_server_error() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executor.write(() -> {
            attempts.incrementAndGet();
            throw HttpServerErrorException.create(
                HttpStatusCode.valueOf(503), "down", null, null, null);
        })).isInstanceOf(HttpServerErrorException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }
}
