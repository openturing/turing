/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an upstream provider (LLM API, search engine, vector store) fails
 * with a non-retryable error or is unreachable after the resilience pipeline
 * gives up. Maps to HTTP 502 (Bad Gateway).
 *
 * <p>Carries the provider identifier as a structured field so clients can
 * surface meaningful context without parsing the message.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurProviderException extends TurApiException {

    private final String provider;

    public TurProviderException(String provider, String message) {
        super(HttpStatus.BAD_GATEWAY, TurErrorTypes.PROVIDER_FAILURE, message);
        this.provider = provider;
    }

    public TurProviderException(String provider, String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, TurErrorTypes.PROVIDER_FAILURE, message, cause);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
