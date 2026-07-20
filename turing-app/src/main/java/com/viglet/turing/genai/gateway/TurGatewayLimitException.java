/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

/**
 * T742 / §XLIX — signals that a gateway request was refused by a per-key limit
 * (rate limit or hard budget cap). The controller maps it to an OpenAI-shaped
 * HTTP 429 carrying {@link #getType()} as the error type.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurGatewayLimitException extends RuntimeException {

    private final transient String type;

    private TurGatewayLimitException(String message, String type) {
        super(message);
        this.type = type;
    }

    /** OpenAI error type, e.g. {@code rate_limit_exceeded} / {@code insufficient_quota}. */
    public String getType() {
        return type;
    }

    public static TurGatewayLimitException rateLimited() {
        return new TurGatewayLimitException(
                "Rate limit exceeded for this virtual key.", "rate_limit_exceeded");
    }

    public static TurGatewayLimitException quotaExceeded() {
        return new TurGatewayLimitException(
                "Monthly budget cap reached for this virtual key.", "insufficient_quota");
    }
}
