/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.openai.errors.OpenAIServiceException;
import com.viglet.turing.api.exception.TurQuotaExhaustedException;

/**
 * T1092 — a provider's (or the tenant's) quota or rate limit, read out of a
 * failed gateway call so the client gets the 429 an OpenAI client dispatches on
 * instead of a 502 it reads as an outage.
 *
 * <p>The status is found the way the provider exposes it: the OpenAI SDK's
 * {@link OpenAIServiceException} (status, type, code and headers), the
 * hand-rolled streaming model's {@link WebClientResponseException}, and the
 * leading {@code "429:"} other SDKs (Anthropic, Gemini) prefix onto their
 * message. The tenant's monthly token budget ({@link TurQuotaExhaustedException})
 * is Turing's own quota and answers the same way.</p>
 *
 * @param message   the provider's message, passed through as OpenAI does
 * @param type      the OpenAI error type: {@code insufficient_quota} or
 *                  {@code rate_limit_exceeded} unless the provider named one
 * @param code      the OpenAI error code
 * @param retryAfter the provider's {@code Retry-After}, or {@code null}
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurGatewayProviderLimit(String message, String type, String code, String retryAfter) {

    static final String INSUFFICIENT_QUOTA = "insufficient_quota";
    static final String RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String RETRY_AFTER = "Retry-After";
    private static final Pattern LEADING_429 = Pattern.compile("^\\s*429\\b");

    /** The limit behind {@code error}, or empty when it was anything else. */
    public static Optional<TurGatewayProviderLimit> of(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            Optional<TurGatewayProviderLimit> limit = fromOne(t);
            if (limit.isPresent()) {
                return limit;
            }
        }
        return Optional.empty();
    }

    private static Optional<TurGatewayProviderLimit> fromOne(Throwable t) {
        if (t instanceof TurQuotaExhaustedException quota) {
            return Optional.of(new TurGatewayProviderLimit(quota.getMessage(), INSUFFICIENT_QUOTA,
                    INSUFFICIENT_QUOTA, null));
        }
        if (t instanceof OpenAIServiceException sdk && sdk.statusCode() == TOO_MANY_REQUESTS) {
            String kind = kindOf(sdk.getMessage() + " " + sdk.code().orElse("") + " " + sdk.type().orElse(""));
            return Optional.of(new TurGatewayProviderLimit(sdk.getMessage(),
                    sdk.type().filter(s -> !s.isBlank()).orElse(kind),
                    sdk.code().filter(s -> !s.isBlank()).orElse(kind),
                    sdk.headers().values(RETRY_AFTER).stream().findFirst().orElse(null)));
        }
        if (t instanceof WebClientResponseException web && web.getStatusCode().value() == TOO_MANY_REQUESTS) {
            String kind = kindOf(web.getMessage() + " " + web.getResponseBodyAsString());
            return Optional.of(new TurGatewayProviderLimit(web.getMessage(), kind, kind,
                    web.getHeaders().getFirst(RETRY_AFTER)));
        }
        String message = t.getMessage();
        if (message != null && LEADING_429.matcher(message).find()) {
            String kind = kindOf(message);
            return Optional.of(new TurGatewayProviderLimit(message, kind, kind, null));
        }
        return Optional.empty();
    }

    /** Out of credits is a quota; anything else answering 429 is a rate limit. */
    private static String kindOf(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("quota") || lower.contains("credit") || lower.contains("billing")
                ? INSUFFICIENT_QUOTA
                : RATE_LIMIT_EXCEEDED;
    }
}
