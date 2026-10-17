/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import com.openai.models.ErrorObject;
import com.viglet.turing.api.exception.TurQuotaExhaustedException;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ErrorEnvelope;

/**
 * T1092 — a provider's 429 (out of credits, or rate limited) and the tenant's
 * token budget reach the gateway client as 429 with the OpenAI error type;
 * only a provider that did not answer is a 502.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiCompatibleGatewayErrorTest {

    private static RateLimitException openAi429(String type, String code, String retryAfter) {
        Headers.Builder headers = Headers.builder();
        if (retryAfter != null) {
            headers.put("Retry-After", retryAfter);
        }
        return RateLimitException.builder()
                .headers(headers.build())
                .error(ErrorObject.builder()
                        .message("You have no credits remaining.")
                        .type(type)
                        .code(code)
                        .param((String) null)
                        .build())
                .build();
    }

    @Test
    void openAiOutOfCreditsIs429InsufficientQuotaWithRetryAfter() {
        ResponseEntity<ErrorEnvelope> response = TurOpenAiCompatibleGatewayAPI.upstreamError(
                openAi429("insufficient_quota", "insufficient_quota", "20"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().error().type()).isEqualTo("insufficient_quota");
        assertThat(response.getBody().error().code()).isEqualTo("insufficient_quota");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("20");
    }

    @Test
    void aProviderLimitWrappedByTheResiliencePipelineIsStillFound() {
        ResponseEntity<ErrorEnvelope> response = TurOpenAiCompatibleGatewayAPI.upstreamError(
                new IllegalStateException("call failed", openAi429("requests", "rate_limit_exceeded", null)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().error().code()).isEqualTo("rate_limit_exceeded");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    void aLeading429MessageFromAnotherSdkIsReadToo() {
        ResponseEntity<ErrorEnvelope> quota = TurOpenAiCompatibleGatewayAPI.upstreamError(
                new RuntimeException("429: You have no credits remaining. Add credits to continue"));
        ResponseEntity<ErrorEnvelope> rate = TurOpenAiCompatibleGatewayAPI.upstreamError(
                new RuntimeException("429 Too Many Requests"));

        assertThat(quota.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(quota.getBody().error().type()).isEqualTo("insufficient_quota");
        assertThat(rate.getBody().error().type()).isEqualTo("rate_limit_exceeded");
    }

    @Test
    void theStreamingTransportsWebClient429IsRead() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "5");
        WebClientResponseException web = WebClientResponseException.create(429, "Too Many Requests", headers,
                "{\"error\":{\"code\":\"rate_limit_exceeded\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        ResponseEntity<ErrorEnvelope> response = TurOpenAiCompatibleGatewayAPI.upstreamError(web);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().error().type()).isEqualTo("rate_limit_exceeded");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    }

    @Test
    void theTenantTokenBudgetIs429InsufficientQuota() {
        ResponseEntity<ErrorEnvelope> response = TurOpenAiCompatibleGatewayAPI.upstreamError(
                new TurQuotaExhaustedException(HttpStatus.PAYMENT_REQUIRED, "llm-tokens", 10, 10, null, null,
                        "Monthly LLM token budget exhausted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().error().type()).isEqualTo("insufficient_quota");
    }

    @Test
    void aProviderThatDidNotAnswerIsStill502() {
        ResponseEntity<ErrorEnvelope> response = TurOpenAiCompatibleGatewayAPI.upstreamError(
                new RuntimeException("Connection refused"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().error().type()).isEqualTo("api_error");
    }
}
