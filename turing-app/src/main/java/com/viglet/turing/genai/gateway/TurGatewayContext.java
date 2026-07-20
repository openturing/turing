/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import com.viglet.turing.persistence.model.gateway.TurGatewayKey;

/**
 * T741 / §XLIX — per-request holder for the authenticated {@link TurGatewayKey}.
 * The bearer filter sets it on the request thread; the gateway service reads it
 * to enforce model scope (and, from T742, to attribute spend by {@code keyId}
 * and apply per-key budget/rate limits). Always cleared in the filter's
 * {@code finally} so no key leaks across pooled request threads.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurGatewayContext {

    private static final ThreadLocal<TurGatewayKey> CURRENT = new ThreadLocal<>();

    private TurGatewayContext() {
    }

    public static void set(TurGatewayKey key) {
        CURRENT.set(key);
    }

    /** The authenticated key for this request, or {@code null} in open mode / off-gateway. */
    public static TurGatewayKey get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
